package com.judge.api.dto;

import com.judge.domain.Problem;

/** Result of a grader upsert / lookup: the local problem id to submit against + applied hash. */
public record ManagedProblemResponse(Long id, String slug, String externalKey, String packageHash, boolean changed) {

    public static ManagedProblemResponse from(Problem p, boolean changed) {
        return new ManagedProblemResponse(p.getId(), p.getSlug(), p.getExternalKey(), p.getPackageHash(), changed);
    }
}
