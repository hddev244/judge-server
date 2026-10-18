package com.judge.domain;

/**
 * ADMIN  — full access, including /api/v1/admin/** and key management.
 * CLIENT — legacy non-admin key: submit to non-private problems, read submissions.
 * GRADER — grading-only key handed to an upstream platform: may push/remove the problems it
 *          manages (/api/v1/grader/**) and judge submissions on those problems only.
 */
public enum ApiKeyRole {
    ADMIN, CLIENT, GRADER
}
