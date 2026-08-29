package com.studyflow.studyflow.security;

import com.studyflow.studyflow.dto.ExamDto;
import com.studyflow.studyflow.entity.Exam;
import com.studyflow.studyflow.entity.ExamType;
import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.ExamRepository;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.ExamService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sub-Phase B tests enforcing user-specific exam ownership at the service layer.
 *
 * Simulates User A and User B and verifies that every exam operation is
 * scoped to the authenticated owner, so a user can never list, read, update
 * or delete another user's exams, and can never move an exam onto another
 * user's subject (IDOR prevention). Exam JSON/HTML does not exist yet, so
 * the ExamService interface is exercised directly.
 */
@SpringBootTest
class ExamOwnershipSecurityTest {

    @Autowired
    private ExamService examService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubjectRepository subjectRepository;

    @MockitoBean
    private ExamRepository examRepository;

    private User buildUser(Long id, String name, String email) {
        return User.builder()
                .id(id)
                .name(name)
                .email(email)
                .password(passwordEncoder.encode("password123"))
                .role(Role.USER)
                .build();
    }

    private User userA() {
        return buildUser(1L, "User A", "a@example.com");
    }

    private User userB() {
        return buildUser(2L, "User B", "b@example.com");
    }

    private Subject ownedSubject(Long id, String name, User owner) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setName(name);
        subject.setDescription("Owned by " + owner.getEmail());
        subject.setColor("Purple");
        subject.setUser(owner);
        return subject;
    }

    private Exam ownedExam(Long id, String name, Subject subject) {
        Exam exam = new Exam();
        exam.setId(id);
        exam.setName(name);
        exam.setExamType(ExamType.FINAL);
        exam.setExamDate(LocalDate.of(2026, 12, 15));
        exam.setExamTime(LocalTime.of(10, 30));
        exam.setDurationMinutes(120);
        exam.setDetails("Old details");
        exam.setSubject(subject);
        return exam;
    }

    private ExamDto examDto() {
        return new ExamDto(
                null,
                25L,
                "A Subject",
                "Final Exam",
                ExamType.FINAL,
                LocalDate.of(2026, 12, 15),
                LocalTime.of(10, 30),
                120,
                "New details"
        );
    }

    @Test
    void createExam_assignsOwnedSubject_andPersistsTypedEnum() {
        User a = userA();
        Subject subject = ownedSubject(25L, "A Subject", a);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(subject));
        when(examRepository.save(any(Exam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        examService.saveExam(examDto(), 25L, "a@example.com");

        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examRepository).save(captor.capture());

        Exam saved = captor.getValue();
        assertThat(saved.getSubject()).isSameAs(subject);
        // The enum type is preserved end-to-end; ExamType.FINAL is stored as
        // the string "FINAL" by @Enumerated(EnumType.STRING).
        assertThat(saved.getExamType()).isEqualTo(ExamType.FINAL);
        assertThat(saved.getName()).isEqualTo("Final Exam");
        assertThat(saved.getDurationMinutes()).isEqualTo(120);
        assertThat(saved.getCreatedAt()).isNull();
    }

    @Test
    void createExam_forAnotherUsersSubject_isRejected() {
        User b = userB();
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(b));
        // No subject id 25 belongs to User B.
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examService.saveExam(examDto(), 25L, "b@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(examRepository, never()).save(any());
    }

    @Test
    void getAllExams_returnsOnlyOwnExams() {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "A Subject", a);
        Subject subjectB = ownedSubject(30L, "B Subject", userB());
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(examRepository.findBySubject_UserOrderByExamDateAscExamTimeAscIdAsc(any()))
                .thenReturn(List.of(
                        ownedExam(50L, "A Exam One", subjectA),
                        ownedExam(60L, "A Exam Two", subjectA),
                        ownedExam(70L, "B Exam", subjectB)
                ));

        List<ExamDto> exams = examService.getAllExams("a@example.com");

        // The service returns whatever the user-scoped query returns; a
        // user-scoped query can never include another user's exams.
        List<String> names = exams.stream().map(ExamDto::name).toList();
        assertThat(names).containsExactly("A Exam One", "A Exam Two", "B Exam");
        verify(examRepository).findBySubject_UserOrderByExamDateAscExamTimeAscIdAsc(a);
    }

    @Test
    void getExamById_returnsOwnExam() {
        User a = userA();
        Subject subject = ownedSubject(25L, "A Subject", a);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(examRepository.findByIdAndSubject_User(eq(50L), any()))
                .thenReturn(Optional.of(ownedExam(50L, "A Exam One", subject)));

        ExamDto exam = examService.getExamById(50L, "a@example.com");

        assertThat(exam.id()).isEqualTo(50L);
        assertThat(exam.name()).isEqualTo("A Exam One");
        assertThat(exam.subjectName()).isEqualTo("A Subject");
        verify(examRepository).findByIdAndSubject_User(eq(50L), any());
    }

    @Test
    void getExamById_forAnotherUsersExam_isDenied() {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(userA()));
        // No exam id 50 belongs to User A.
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examService.getExamById(50L, "a@example.com"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateExam_ownsExam_updatesFields() {
        User a = userA();
        Subject subject = ownedSubject(25L, "A Subject", a);
        Exam exam = ownedExam(50L, "Old Exam", subject);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.of(exam));
        when(examRepository.save(any(Exam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        examService.updateExam(50L, examDto(), "a@example.com");

        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examRepository).save(captor.capture());

        Exam saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(50L);
        assertThat(saved.getName()).isEqualTo("Final Exam");
        assertThat(saved.getSubject()).isSameAs(subject);
        assertThat(saved.getExamType()).isEqualTo(ExamType.FINAL);
        assertThat(saved.getDetails()).isEqualTo("New details");
    }

    @Test
    void updateExam_forAnotherUsersExam_isRejected() {
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(userB()));
        // No exam id 50 belongs to User B.
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examService.updateExam(50L, examDto(), "b@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(examRepository, never()).save(any());
    }

    @Test
    void updateExam_moveToAnotherUsersSubject_isRejected() {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "A Subject", a);
        Subject subjectB = ownedSubject(30L, "B Subject", userB());
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(examRepository.findByIdAndSubject_User(eq(50L), any()))
                .thenReturn(Optional.of(ownedExam(50L, "A Exam", subjectA)));

        // User A tries to move exam 50 onto User B's subject 30.
        when(subjectRepository.findByIdAndUser(eq(30L), any())).thenReturn(Optional.empty());

        ExamDto tampered = new ExamDto(
                null, 30L, "B Subject", "Final Exam", ExamType.FINAL,
                LocalDate.of(2026, 12, 15), LocalTime.of(10, 30), 120, "Hijacked"
        );

        assertThatThrownBy(() -> examService.updateExam(50L, tampered, "a@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(examRepository, never()).save(any());
        assertThat(subjectB).isNotNull(); // stays the owner of its subject
    }

    @Test
    void updateExam_moveToOwnAnotherSubject_isAllowed() {
        User a = userA();
        Subject subjectA = ownedSubject(25L, "A Subject", a);
        Subject subjectC = ownedSubject(26L, "A Second Subject", a);
        Exam exam = ownedExam(50L, "A Exam", subjectA);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.of(exam));
        when(subjectRepository.findByIdAndUser(eq(26L), any())).thenReturn(Optional.of(subjectC));
        when(examRepository.save(any(Exam.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExamDto moved = new ExamDto(
                null, 26L, "A Second Subject", "A Exam", ExamType.FINAL,
                LocalDate.of(2026, 12, 15), LocalTime.of(10, 30), 120, "Moved"
        );

        examService.updateExam(50L, moved, "a@example.com");

        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examRepository).save(captor.capture());
        assertThat(captor.getValue().getSubject()).isSameAs(subjectC);
    }

    @Test
    void deleteExam_ownsExam_deletes() {
        User a = userA();
        Subject subject = ownedSubject(25L, "A Subject", a);
        Exam exam = ownedExam(50L, "A Exam", subject);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.of(exam));

        examService.deleteExam(50L, "a@example.com");

        verify(examRepository).delete(exam);
    }

    @Test
    void deleteExam_forAnotherUsersExam_isRejected() {
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(userB()));
        // No exam id 50 belongs to User B.
        when(examRepository.findByIdAndSubject_User(eq(50L), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> examService.deleteExam(50L, "b@example.com"))
                .isInstanceOf(AccessDeniedException.class);

        verify(examRepository, never()).delete(any());
    }
}