package com.bot.bot.service;

import com.bot.bot.entities.Answer;
import com.bot.bot.entities.Questions;
import com.bot.bot.entities.User;
import com.bot.bot.repositories.AnswerRepository;
import com.bot.bot.repositories.QuestionsRepository;
import com.bot.bot.repositories.UserRepository;
import jakarta.transaction.Transactional;
import org.aspectj.weaver.patterns.TypePatternQuestions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BotServiceImpl implements  BotService{

    @Autowired
     private UserRepository  userRepository;

      @Autowired
     private AnswerRepository answerRepository;

      @Autowired
     private QuestionsRepository questionsRepository;

    @Override
    public  List<Questions> getQuestions() {
        return  questionsRepository.findAll();
    }
    
    


    @Override
    public void checkAnswer(User user, Questions question, String userAnswer, boolean isCorrect) {
        Answer answer = new Answer();
        answer.setUser(user);
        answer.setQuestion(question);
        answer.setUserAnswer(userAnswer);
        answer.setCorrect(isCorrect);

        answerRepository.save(answer);

    }

    @Override
    public User getFinalWinner() {

            List<User> users = userRepository.findAll();
            return users.stream()
                    .max((u1, u2) -> Integer.compare(u1.getScore(), u2.getScore()))
                    .orElse(null); // Returns null if there are no users

    }

    @Override
    @Transactional
    public User getOrCreateUser(Long chatId, String userName) {
        User user= userRepository.findByChatId(chatId);
        if(user==null){
            User newUser = new User();
            newUser.setChatId(chatId);
            newUser.setUserName(userName);
            return userRepository.save(newUser);
        }

        return user;
    }

    @Override
    public Answer saveAnswer(Long userId, Long questionId, String userAnswer ,boolean isCorrect) {
        User user = userRepository.findByChatId(userId);
        Questions questions= questionsRepository.findById(questionId).get();
        Answer answer = new Answer();
        answer.setUserAnswer(userAnswer);
        answer.setQuestion(questions);
        answer.setCorrect(isCorrect);
        answer.setUser(user);
        return  answerRepository.save(answer);
    }

    @Override
    public int getUSerScore(Long chatId) {
        User user = userRepository.findByChatId(chatId);
        return user != null ? user.getScore() : 0;
    }

    @Override
    public List<Answer> findUser_chatId(Long chatId) {
        return answerRepository.findByUser_ChatId(chatId);
    }

    @Override
    public boolean ifAlreadyAnswered(Long userId, Long questionId) {
        Optional<Answer> answer = answerRepository.findByUser_IdAndQuestion_Id(userId,questionId);
        return answer.isPresent();
    }

    @Override
    public boolean hasCompletedQuiz(Long chatId) {
        User user = userRepository.findByChatId(chatId);
        return user != null && user.isHasCompletedQuiz(); // Make sure user exists before accessing the field
    }

    @Override
    public User getUserByChatId(Long chatId) {
        return  userRepository.findByChatId(chatId);
    }

    @Override
    public void updateUser(User user) {
        userRepository.save(user);
    }

    @Override
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public void setHasCompletedQuiz(Long chatId) {
        User user = userRepository.findByChatId(chatId);
        user.setHasCompletedQuiz(true);
        userRepository.save(user);
    }


}
