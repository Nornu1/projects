package com.bot.bot.service;
import com.bot.bot.entities.Answer;
import com.bot.bot.entities.Questions;
import com.bot.bot.entities.User;
import com.bot.bot.repositories.QuestionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


public interface BotService {

    List<Questions> getQuestions();
    void checkAnswer(User user, Questions question, String userAnswer, boolean isCorrect);
    User getFinalWinner();
    User getOrCreateUser( Long chatId ,String UserName);
    Answer saveAnswer(Long user, Long question , String userAnswer ,boolean isCorrect);
    int getUSerScore(Long chatId);
    List<Answer> findUser_chatId(Long  chatId);
    boolean ifAlreadyAnswered(Long userId, Long questionId);
    boolean hasCompletedQuiz(Long chatId);
    User getUserByChatId(Long chatId);
    void  updateUser(User user);
    List<User>findAllUsers();
    void  setHasCompletedQuiz(Long chatId);

}
