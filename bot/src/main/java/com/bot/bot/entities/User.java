package com.bot.bot.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "User")
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    private Long version;

    @Column(name = "chat_id", nullable = false, unique = true)
    private Long chatId;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Answer> answers = new ArrayList<>();

    @Column(name = "user_name" , nullable = false)
    private String userName;

    @Column( nullable = false, columnDefinition = "boolean default false")
    private boolean hasCompletedQuiz = false;

    public int getScore() {
        return (int) answers.stream()
                .filter(Answer::isCorrect)
                .count();
    }

}