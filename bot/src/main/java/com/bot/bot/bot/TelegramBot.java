package com.bot.bot.bot;


import com.bot.bot.entities.Answer;
import com.bot.bot.entities.Questions;
import com.bot.bot.entities.User;
import com.bot.bot.service.BotService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Component
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer  {

    private final TelegramClient telegramClient;

    private final Map<Long, UserSession> userSessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    //    @Autowired
//    SendMessage sendMessage;
    private final Long groupId = -1002430108890L;
    private  Integer leaderBoardInGroupChat;

    private final BotService botService;
//    private Integer currentMessageId = null;

    private  String lastGroupUpdate="";

    @Autowired
    public TelegramBot(TelegramClient telegramClient, BotService botService) {
//
        this.telegramClient= telegramClient;
        this.botService = botService;

    }

    @Override
    public String getBotToken() {
        return "7918312232:AAFwyop5KSd9YW64n3n0c4t5Bbunb6UfpJY";
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }


    @Override
    public void consume(Update update) {
        long chatId;
        if (update.hasCallbackQuery()) {
            chatId = update.getCallbackQuery().getMessage().getChatId();
            String userAnswer = update.getCallbackQuery().getData();
            UserSession session = userSessions.get(chatId);
            User user = botService.getOrCreateUser(chatId, update.getCallbackQuery().getFrom().getUserName());

            if (session != null) {
                deleteQuestionMessage(chatId).thenCompose(deleted -> {
                    Questions currentQuestion = session.getCurrentQuestion();
                    boolean isCorrect = currentQuestion.getCorrectAnswer().equals(userAnswer);
                    botService.saveAnswer(chatId, currentQuestion.getId(), userAnswer, isCorrect);
//                  if(isCorrect){
//                      session.incrementScore()
//                    }
//                 updateGroupScoreInChat( user.getUserName(), session.getUserCurrentScore());
                    updateGroupScores();
                    session.nextQuestion();

                    if (session.hasMoreQuestions()) {
                        return askQuestion(chatId, user, null); // Pass groupId if necessary
                    } else {
                        session.stopTimer();
                        return sendCongratulatoryMessage(chatId);
                    }
                }).exceptionally(e -> {
                    notifyUserNetworkIssue(chatId, "Unable to update your messages.. seems like you have network issues");
                    System.out.println("Error handling callback: " + e);
                    return null;
                });
            } else {
                sendMessageWithRetry(chatId, createSendMessage(chatId,"Your session has expired. Please start a new quiz with /next."), 3);
            }
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            chatId = update.getMessage().getChatId();
            UserSession session = userSessions.get(chatId);

            if (session != null && session.isInQuiz()) {
                sendMessageWithRetry(chatId, createSendMessage(chatId,"You are currently taking a quiz. Please answer using the buttons provided."), 5);
                return;
            }

            handleUserMessage(update);
        }
    }
    private void handleUserMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        String messageText = update.getMessage().getText();
        String userName = update.getMessage().getChat().getUserName();

        if (userName == null) {
            sendMessageWithRetry(chatId, createSendMessage(chatId,"Please set your user name in your profile and then type /next to start."), 3);
        } else {
            User user = botService.getOrCreateUser(chatId, userName);
            switch (messageText.toLowerCase()) {
                case "/start":
                    sendWelcomeMessage(chatId);
                    break;
                case "/next":
                    if (botService.hasCompletedQuiz(chatId)) {
                        sendMessageWithRetry(chatId, createSendMessage(chatId,"You have already taken the quiz. You cannot take it again."), 3);
                    } else {
                        askQuestion(chatId, user, null);
                    }
                    break;
                default:
                    sendMessageWithRetry(chatId, createSendMessage(chatId,"Please click /start to proceed."), 5);
                    break;
            }
        }
    }
    private void notifyUserNetworkIssue(long chatId, String messageText) {
        sendMessageWithRetry(chatId, createSendMessage(chatId,messageText), 5);
    }
    private SendMessage createSendMessage(Long chatId,String text) {
        return SendMessage.builder().chatId(Long.toString(chatId)).text(text).build();
    }

    private Message sendMessageWithRetry(long chatId, SendMessage message, int retryCount) {
        for (int retries = 0; retries < retryCount; retries++) {
            try {
                return telegramClient.execute(message);
            } catch (TelegramApiException e) {
                if (retries == retryCount - 1) {
                    notifyUserNetworkIssue(chatId, "Failed to send the message after several attempts. Please check your connection.");
                }
                try {
                    Thread.sleep((long) Math.pow(2, retries) * 1000); // Exponential backoff
                } catch (InterruptedException ignored) {}
            }
        }
        return null;
    }

    private void sendWelcomeMessage(Long chatId) {
//        deleteQuestionMessage(chatId);
        String welcomeText = "Welcome to the quiz! Make sure you have a username set up in your profile, then click /next to start.";
        SendMessage sendMessage = SendMessage.builder().chatId(Long.toString(chatId)).text(welcomeText).build();
        sendMessageWithRetry(chatId, sendMessage,3);
    }


    private CompletableFuture<Void> deleteQuestionMessage(Long chatId) {
        return CompletableFuture.runAsync(() -> {
            UserSession session = userSessions.get(chatId);
            if (session != null && session.getCurrentQuestionMessageId() != null) {
                try {
                    DeleteMessage deleteMessage = new DeleteMessage(String.valueOf(chatId), session.getCurrentQuestionMessageId());
                    telegramClient.execute(deleteMessage);
                    session.setCurrentQuestionMessageId(null);
                } catch (TelegramApiException e) {
                    System.out.println("Failed to delete question message: " + e.getMessage());
                }
            }
        });
    }



    private CompletableFuture<Void> sendCongratulatoryMessage(Long chatId) {
        return CompletableFuture.runAsync(() -> {
            List<Answer> answers = botService.findUser_chatId(chatId);
            int score = (int) answers.stream().filter(Answer::isCorrect).count();
            String message = "🎉  Congratulations! You've completed the quiz!\nYour score: " + score + "/10";
            sendMessageWithRetry(chatId, createSendMessage(chatId,message), 3);
            User user = botService.getUserByChatId(chatId);
            user.setHasCompletedQuiz(true);
            botService.updateUser(user);
            userSessions.remove(chatId);
            updateGroupScores();
        });
    }
    private void updateGroupScores() {
        List<User> users = botService.findAllUsers(); // Fetch users in the group
        StringBuilder leaderboard = new StringBuilder("🏆 **Leaderboard** 🏆\n\n");

        // Create a list to hold user scores for sorting
        List<UserScore> userScores = new ArrayList<>();

        for (User user : users) {
            List<Answer> answers = botService.findUser_chatId(user.getChatId()); // Fetch answers for each user
            int score = 0;

            for (Answer answer : answers) {
                if (answer.isCorrect()) {
                    score++;
                }
            }

            // Add user scores to the list
            userScores.add(new UserScore(user.getUserName(), score,user.isHasCompletedQuiz()));
        }


        // Sort users by score in descending order
        userScores.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));

        // Append each user's score with ranking
        for (int i = 0; i < userScores.size(); i++) {
            UserScore userScore = userScores.get(i);
//            String status= check(userScore.isHasCompletedQuiz());
            leaderboard.append(String.format("%d. **%s**: %d points out of 10**:** status** :%s\n", i + 1, userScore.getUserName(), userScore.getScore(),check(userScore.isHasCompletedQuiz())));
        }

        leaderboard.append("\n🎉 Good luck to all participants! 🎉");

        // Check if leaderboard message exists
        if (leaderBoardInGroupChat == null) {
            SendMessage groupMessage = SendMessage.builder()
                    .chatId(Long.toString(groupId))
                    .text(leaderboard.toString())
                    .parseMode("Markdown") // or "HTML"
                    .build();

            Message sendMessage = sendMessageWithRetry(groupId, groupMessage, 4);
//            System.out.println("ID for content is: " + sendMessage.getMessageId());
//            System.out.println("Updated group scores.");
            leaderBoardInGroupChat = sendMessage.getMessageId();
        } else {
            EditMessageText editMessage = new EditMessageText("");
            editMessage.setChatId(Long.toString(groupId));
            editMessage.setMessageId(Integer.valueOf(String.valueOf(leaderBoardInGroupChat)));
            editMessage.setText(leaderboard.toString());
            editMessage.setParseMode("Markdown"); // Ensure consistent formatting
            if (!lastGroupUpdate.equals(String.valueOf(editMessage))) {
                try {
                    telegramClient.execute(editMessage); // Execute the edit
                    lastGroupUpdate=String.valueOf(editMessage);
                    System.out.println("Leaderboard updated successfully.");
                } catch (TelegramApiException e) {
                    System.out.println("Error updating leaderboard: " + e.getMessage());
                    e.printStackTrace();
                }
            }

        }
    }



    private CompletableFuture<Void> askQuestion(Long chatId, User user, Long groupId) {
        return CompletableFuture.supplyAsync(() -> {
            UserSession session = userSessions.computeIfAbsent(chatId, k -> new UserSession(botService.getQuestions(), telegramClient, botService));
            session.startQuiz();
            return session;
        }).thenCompose(session -> {
            Questions currentQuestion = session.getCurrentQuestion();
            if (currentQuestion != null) {
                return sendQuestion(chatId, currentQuestion).thenRun(() -> {
                    session.startTimer(scheduler, chatId, (ignored) -> {
                        deleteQuestionMessage(chatId).thenRun(() -> {
                            userSessions.remove(chatId);
                            session.stopTimer();
                            sendCongratulatoryMessage(chatId);
                        });
                    });
                });
            } else {
                session.stopTimer();
                return sendCongratulatoryMessage(chatId);
            }
        }).exceptionally(e -> {
            System.out.println("Error in askQuestion: " + e.getMessage());
            return null;
        });
    }

    private CompletableFuture<Void> sendQuestion(Long chatId, Questions question) {
        String message = "Question: <strong><i>" + question.getQuestion() + "</i> ❓</strong>";
        List<InlineKeyboardRow> rows = Arrays.asList(
                createButtonRow("1️⃣. " + question.getOption1(), question.getOption1()),
                createButtonRow("2️⃣. " + question.getOption2(), question.getOption2()),
                createButtonRow("3️⃣. " + question.getOption3(), question.getOption3()),
                createButtonRow("4️⃣. " + question.getOption4(), question.getOption4())
        );
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup(rows);
        SendMessage messageToSend = SendMessage.builder()
                .chatId(Long.toString(chatId))
                .text(message)
                .replyMarkup(keyboardMarkup)
                .parseMode("HTML")
                .build();

        return CompletableFuture.runAsync(() -> {
            Message sentMessage = sendMessageWithRetry(chatId, messageToSend, 5);
            if (sentMessage != null) {
                UserSession session = userSessions.get(chatId);
                if (session != null) {
                    session.setCurrentQuestionMessageId(sentMessage.getMessageId());
                }
            }
        });
    }
    private String check(boolean status){
        if(status)
        {
            return "completed";
        }
        else{
            return "in quiz";
        }
    }

    private InlineKeyboardRow createButtonRow(String text, String callbackData) {
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
        return new InlineKeyboardRow(Collections.singletonList(button));
    }

    private static class UserSession {
        private final List<Questions> questionsList;
        private int currentQuestionIndex;
        private long totalTime; // Total quiz duration in seconds
        private ScheduledFuture<?> timerFuture;
        private ScheduledFuture<?> updateFuture; // For sending periodic updates
        private  Long chatId;
        private  String messageId;
        @Autowired
        private final TelegramClient telegramClient;
        @Autowired
        private final   BotService botService;
        private boolean isInQuiz;
        @Getter
        @Setter
        private Integer currentQuestionMessageId;


        @Getter
        @Setter
        private  int userCurrentScore;

//        private ScheduledFuture<?> timerFuture;
public void incrementScore() {
    this.userCurrentScore++; // Method to increment score
}

        public boolean isInQuiz() {
            return isInQuiz;
        }

        public void setInQuiz(boolean inQuiz) {
            this.isInQuiz = inQuiz;
        }
        public void startQuiz() {
            setInQuiz(true);
            // Start the quiz logic...
        }

        public void endQuiz() {
            setInQuiz(false);
            // End the quiz logic...
        }
        public UserSession(List<Questions> questionsList, TelegramClient telegramClient, BotService botService) {
            this.telegramClient=telegramClient;
            this.questionsList = questionsList;
            this.currentQuestionIndex = 0;
            this.totalTime= 120;
            this.botService=botService;
            this.userCurrentScore=0;

        }
        public void startTimer(ScheduledExecutorService scheduler, Long chatId, Consumer<Void> onTimeExpired) {
            this.chatId = chatId; // Store chatId
            stopTimer();
            timerFuture = scheduler.scheduleAtFixedRate(() -> {
                if (totalTime > 0) {
                    totalTime--;

                } else {
                    onTimeExpired.accept(null);
                    timerFuture.cancel(false); // Stop the timer
                    updateFuture.cancel(false); // Stop updates
                    stopTimer();
                }
            }, 0, 1, TimeUnit.SECONDS);


            // Periodically send timer updates
//            updateFuture = scheduler.scheduleAtFixedRate(this::sendTimerUpdate, 0, 1, TimeUnit.MINUTES); // Send update every second
            // This will be called every minute
            updateFuture = scheduler.scheduleAtFixedRate(this::sendTimerUpdate, 0, 1, TimeUnit.MINUTES);
            scheduler.scheduleAtFixedRate(() -> {
                if (totalTime <= 40 && totalTime > 0) {
                    sendTimerUpdate(); // Send immediate update when entering the 40 seconds range
                    System.out.println("remaining time in less tha 40 secs>>> "+ totalTime);
                }
            }, 0, 1, TimeUnit.SECONDS); // Check every second for updates
        }
        private  String lastMessageContent= "";// Track the last sent message content

        private void sendTimerUpdate() {
            String formattedTime = formatTime(totalTime); // Get the formatted time
            String message = "⏳ Time remaining: " + formattedTime;

            // Check if the message is different from the last sent message
            if (!message.equals(lastMessageContent)) {
                if (messageId == null) {
                    // Send the initial message and store the message ID
                    SendMessage sendMessage = SendMessage.builder()
                            .chatId(Long.toString(chatId))
                            .text(message)
                            .build();

                    try {
                        lastMessageContent = message;
                        Message sentMessage = telegramClient.execute(sendMessage);
                        messageId = String.valueOf(sentMessage.getMessageId()); // Store the message ID
                        // Update the last message content
                    } catch (TelegramApiException e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    // Edit the existing message
                    EditMessageText editMessage = new EditMessageText("");
                    editMessage.setChatId(Long.toString(chatId));
                    editMessage.setMessageId(Integer.parseInt(messageId));
                    editMessage.setText(message);
                    editMessage.setParseMode("HTML"); // Ensure the parse mode is set if you're using it

                    try {
                        lastMessageContent = message;
                        telegramClient.execute(editMessage);
                        // Execute the edit
                        // Update the last message content
                    } catch (TelegramApiException e) {

                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }

        public void stopTimer() {
            System.out.println("in stop timer ");
            if (timerFuture != null) {
                timerFuture.cancel(false);
            }
            if (updateFuture != null) {
                updateFuture.cancel(false);
            }
        }


        public Questions getCurrentQuestion() {
            if (currentQuestionIndex < questionsList.size()) {
                return questionsList.get(currentQuestionIndex);
            }
            return null; // Return null if no more questions
        }

        public void nextQuestion() {
            currentQuestionIndex++;
        }

        public boolean hasMoreQuestions() {
            return currentQuestionIndex < questionsList.size();
        }
        private String formatTime(long totalTimeInSeconds) {
            long seconds = totalTimeInSeconds % 60;
            long minutes = totalTimeInSeconds / 60;
            return String.format("%02d:%02d", minutes,seconds);
        }

    }
    private void updateGroupScoreInChat( String userName, int score) {
        String message = String.format("🎉 **%s**'s current points: %d points 🎉", userName, score);

        // Check if a previous message exists for the user's score
        if (leaderBoardInGroupChat == null) {
            SendMessage groupMessage = SendMessage.builder()
                    .chatId(Long.toString(groupId))
                    .text(message)
                    .parseMode("Markdown") // or "HTML"
                    .build();

            // Send the message
            sendMessageWithRetry(groupId, groupMessage, 4);
        } else {
            EditMessageText editMessage = new EditMessageText("");
            editMessage.setChatId(Long.toString(groupId));
            editMessage.setMessageId(Integer.valueOf(String.valueOf(leaderBoardInGroupChat)));
            editMessage.setText(message);
            editMessage.setParseMode("Markdown"); // Ensure consistent formatting

            try {
                telegramClient.execute(editMessage); // Execute the edit
                System.out.println("User score updated successfully in group chat.");
            } catch (TelegramApiException e) {
                System.out.println("Error updating user score: " + e.getMessage());
                e.printStackTrace();
            }
        }

    }





//    private void deletePreviousWarningMessage(Long chatId) {
//        UserSession session = userSessions.get(chatId);
//        if (session != null && session.getWarningMessageId() != null) {
//            try {
////                DeleteMessage deleteMessage = new DeleteMessage(String.valueOf(chatId), session.getWarningMessageId());
////                telegramClient.execute(deleteMessage);
////                session.setWarningMessageId(null); // Clear the warning message ID after successful deletion
//            } catch (TelegramApiException e) {
//                System.out.println("Failed to delete warning message: " + e.getMessage());
//                // Optionally, you can log or handle specific error codes (e.g., message not found)
//            }
//        } else {
//            System.out.println("No warning message to delete or message ID is null.");
//        }
//    }
}

//@Override
//public void  userScore (Update update) {
//    long chatId = update.getMessage().getChatId();
//    String messageText = update.getMessage().getText();
//    String username = update.getMessage().getFrom().getUserName();
//
//    User user = quizService.getOrCreateUser(chatId, username);
//
//    if (messageText.equalsIgnoreCase("/score")) {
//        int score = quizService.getUserScore(chatId);
//        sendMessage(chatId, "Your current score is: " + score);
//    }
//    // Other command handling...
//}