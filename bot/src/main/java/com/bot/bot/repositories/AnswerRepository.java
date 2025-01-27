package com.bot.bot.repositories;

import com.bot.bot.entities.Answer;
import com.bot.bot.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findByUser(User user);

    Optional<Answer> findByUser_IdAndQuestion_Id(Long id, Long id1);
    List<Answer> findByUser_ChatId(Long chatId);

}