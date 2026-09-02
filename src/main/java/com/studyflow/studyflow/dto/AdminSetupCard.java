package com.studyflow.studyflow.dto;

/**
 * A single "Complete Your StudyFlow" onboarding card for the ADMIN dashboard.
 *
 * Cards are scoped to the authenticated admin's OWN user-owned records
 * (subjects, topics, tasks, exams, study sessions), matching the
 * application's ownership model: the CTA links always open existing routes
 * that create or manage data belonging to the signed-in admin, so the card
 * state can never contradict its action (and never reads another user's
 * data). The count is a real database value, never hard-coded.
 *
 * @param key         stable identifier ("SUBJECT", "TOPIC", "TASK", "EXAM", "SESSION")
 * @param title       display title, e.g. "Subjects"
 * @param icon        Bootstrap Icons class, e.g. "bi bi-book-half"
 * @param count       the authenticated admin's own record count
 * @param done        true when {@code count} is greater than zero
 * @param statusLabel short state line, e.g. "Not started" or "12 added"
 * @param ctaLabel    call-to-action text, e.g. "Add Your First Subject" or "Manage Subjects"
 * @param ctaUrl      existing route the card links to
 */
public record AdminSetupCard(
        String key,
        String title,
        String icon,
        long count,
        boolean done,
        String statusLabel,
        String ctaLabel,
        String ctaUrl
) {
}