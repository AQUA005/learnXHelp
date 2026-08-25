package com.ustc.learnx.service;

import com.ustc.learnx.entity.ExamQuestion;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scores an exam submission.
 *
 * <p>Deliberately free of repositories and transactions: grading decides a
 * student's marks, so it is kept as plain logic that can be tested directly.
 */
@Service
public class GradingService {

    /** One answer as submitted by a student. */
    public record SubmittedAnswer(Long questionId, String answer) {
    }

    /**
     * @param score     marks awarded
     * @param maxMarks  total marks available across the whole exam
     */
    public record GradingResult(int score, int maxMarks) {
    }

    /**
     * Grades a submission.
     *
     * <p>Marks are awarded per question, and {@code maxMarks} covers every
     * question on the exam rather than only those answered — leaving a question
     * blank has to cost the student those marks.
     *
     * <p>Unrecognised question ids are ignored, so a client cannot add marks by
     * submitting answers to questions that are not on this exam. Where the same
     * question is answered more than once, the first answer counts.
     */
    public GradingResult grade(List<ExamQuestion> questions, List<SubmittedAnswer> answers) {
        int maxMarks = questions == null ? 0
                : questions.stream().mapToInt(ExamQuestion::getPoints).sum();
        if (questions == null || questions.isEmpty() || answers == null || answers.isEmpty()) {
            return new GradingResult(0, maxMarks);
        }

        Map<Long, ExamQuestion> byId = new HashMap<>();
        for (ExamQuestion question : questions) {
            if (question.getId() != null) {
                byId.put(question.getId(), question);
            }
        }

        int score = 0;
        Map<Long, Boolean> alreadyGraded = new HashMap<>();
        for (SubmittedAnswer answer : answers) {
            if (answer == null || answer.questionId() == null) {
                continue;
            }
            ExamQuestion question = byId.get(answer.questionId());
            if (question == null || alreadyGraded.putIfAbsent(question.getId(), true) != null) {
                continue;
            }
            if (isCorrect(question, answer.answer())) {
                score += question.getPoints();
            }
        }
        return new GradingResult(score, maxMarks);
    }

    /**
     * Both question types are matched the same way: trimmed and ignoring case.
     *
     * <p>For a short answer that is a blunt rule — it accepts only an exact
     * wording — but it is the rule the exam was written against, so it stays
     * until answer keys support alternatives.
     */
    private boolean isCorrect(ExamQuestion question, String submitted) {
        // A question with no answer key cannot be auto-marked; award nothing
        // rather than failing the whole submission.
        if (question.getCorrectAnswer() == null || submitted == null) {
            return false;
        }
        return submitted.trim().equalsIgnoreCase(question.getCorrectAnswer().trim());
    }
}
