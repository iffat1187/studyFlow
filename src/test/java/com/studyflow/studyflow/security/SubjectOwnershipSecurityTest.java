package com.studyflow.studyflow.security;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.Subject;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.SubjectRepository;
import com.studyflow.studyflow.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/**
 * Phase 15 tests enforcing user-specific subject ownership.
 *
 * Simulates User A and User B and verifies that every subject operation is
 * scoped to the authenticated owner, so a user can never list, read, edit,
 * update or delete another user's subjects (IDOR prevention).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SubjectOwnershipSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SubjectRepository subjectRepository;

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

    @Test
    void userA_canListOwnSubjectsOnly() throws Exception {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(userA()));
        when(subjectRepository.findAllByUserOrderByIdAsc(any()))
                .thenReturn(List.of(
                        ownedSubject(25L, "A Subject One", userA()),
                        ownedSubject(30L, "A Subject Two", userA())
                ));

        mockMvc.perform(get("/subjects")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("A Subject One")))
                .andExpect(content().string(containsString("A Subject Two")))
                .andExpect(content().string(not(containsString("B Subject"))));
    }

    @Test
    void userB_doesNotSeeUserAsSubjects() throws Exception {
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(userB()));
        when(subjectRepository.findAllByUserOrderByIdAsc(any())).thenReturn(List.of());

        mockMvc.perform(get("/subjects")
                        .with(user("b@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No Subjects Added Yet")))
                .andExpect(content().string(not(containsString("A Subject One"))));
    }

    @Test
    void userB_cannotEditUserAsSubject() throws Exception {
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(userB()));
        // No subject id 25 belongs to User B.
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/subjects/edit/25")
                        .with(user("b@example.com").roles("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("A Subject"))));
    }

    @Test
    void userB_cannotUpdateUserAsSubject() throws Exception {
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(userB()));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/subjects/update/25")
                        .param("name", "Tampered")
                        .param("description", "Hijacked")
                        .param("color", "Red")
                        .with(csrf())
                        .with(user("b@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(subjectRepository, never()).save(any());
    }

    @Test
    void userB_cannotDeleteUserAsSubject() throws Exception {
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(userB()));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/subjects/delete/25")
                        .with(user("b@example.com").roles("USER")))
                .andExpect(status().isForbidden());

        verify(subjectRepository, never()).delete(any());
    }

    @Test
    void userA_canEditOwnSubject() throws Exception {
        User a = userA();
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(subjectRepository.findByIdAndUser(eq(25L), any()))
                .thenReturn(Optional.of(ownedSubject(25L, "A Subject One", a)));

        mockMvc.perform(get("/subjects/edit/25")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Edit Subject")))
                .andExpect(content().string(containsString("A Subject One")));
    }

    @Test
    void userA_canDeleteOwnSubject() throws Exception {
        User a = userA();
        Subject subject = ownedSubject(25L, "A Subject One", a);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(subject));

        mockMvc.perform(get("/subjects/delete/25")
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subjects"));

        verify(subjectRepository).delete(subject);
    }

    @Test
    void createSubject_assignsAuthenticatedUserAsOwner() throws Exception {
        User a = userA();
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(subjectRepository.save(any(Subject.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/subjects/save")
                        .param("name", "New A Subject")
                        .param("description", "Freshly created")
                        .param("color", "Green")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subjects"));

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());

        Subject saved = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(saved.getUser())
                .isNotNull()
                .isSameAs(a);
        org.assertj.core.api.Assertions.assertThat(saved.getName()).isEqualTo("New A Subject");
    }

    @Test
    void updateSubject_keepsOwnership() throws Exception {
        User a = userA();
        Subject existing = ownedSubject(25L, "A Subject One", a);
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(a));
        when(subjectRepository.findByIdAndUser(eq(25L), any())).thenReturn(Optional.of(existing));
        when(subjectRepository.save(any(Subject.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/subjects/update/25")
                        .param("name", "A Subject Updated")
                        .param("description", "Changed by owner")
                        .param("color", "Blue")
                        .with(csrf())
                        .with(user("a@example.com").roles("USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/subjects"));

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());

        Subject saved = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(saved.getName()).isEqualTo("A Subject Updated");
        org.assertj.core.api.Assertions.assertThat(saved.getColor()).isEqualTo("Blue");
        org.assertj.core.api.Assertions.assertThat(saved.getUser()).isSameAs(a);
    }
}