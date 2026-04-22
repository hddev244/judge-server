package com.judge.repository;

import com.judge.domain.Problem;
import com.judge.domain.ProblemTag;
import jakarta.persistence.criteria.*;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

public class ProblemSpecification {

    public static Specification<Problem> isPublished() {
        return (root, query, cb) -> cb.isTrue(root.get("isPublished"));
    }

    public static Specification<Problem> hasDifficulty(String difficulty) {
        return (root, query, cb) -> cb.equal(root.get("difficulty"), difficulty);
    }

    public static Specification<Problem> titleContains(String q) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + q.toLowerCase() + "%");
    }

    public static Specification<Problem> hasTopic(String topicSlug) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<?, ?> topics = root.join("topics", JoinType.INNER);
            return cb.equal(topics.get("slug"), topicSlug);
        };
    }

    public static Specification<Problem> hasCategory(String categorySlug) {
        return (root, query, cb) -> {
            query.distinct(true);
            Join<?, ?> categories = root.join("categories", JoinType.INNER);
            return cb.equal(categories.get("slug"), categorySlug);
        };
    }

    /** Problem must have ALL of the specified tags (AND semantics). */
    public static Specification<Problem> hasTags(List<String> tags) {
        return (root, query, cb) -> {
            // Subquery: problem_id where COUNT(matching tags) = tags.size()
            Subquery<Long> sub = query.subquery(Long.class);
            Root<ProblemTag> tagRoot = sub.from(ProblemTag.class);
            sub.select(tagRoot.get("problem").get("id"))
                    .where(tagRoot.get("tag").in(tags))
                    .groupBy(tagRoot.get("problem").get("id"))
                    .having(cb.equal(cb.count(tagRoot), (long) tags.size()));
            return root.get("id").in(sub);
        };
    }
}
