package com.studyflow.studyflow.service.impl;

import com.studyflow.studyflow.entity.Role;
import com.studyflow.studyflow.entity.User;
import com.studyflow.studyflow.repository.UserRepository;
import com.studyflow.studyflow.service.AdminUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link AdminUserService}.
 *
 * Enforces self-protection: an admin can never disable, demote or delete
 * their own account. All other updates are plain object-graph changes
 * written back through Spring Data.
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;

    public AdminUserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> searchUsers(String searchTerm, Role role) {
        boolean hasRole = role != null;
        boolean hasTerm = StringUtils.hasText(searchTerm);
        String term = hasTerm ? searchTerm.trim() : "";

        if (hasRole && hasTerm) {
            return userRepository
                    .findByRoleAndEmailContainingIgnoreCaseOrRoleAndNameContainingIgnoreCaseOrderByCreatedAtDesc(
                            role, term, role, term);
        }
        if (hasRole) {
            return userRepository.findByRoleOrderByCreatedAtDesc(role);
        }
        if (hasTerm) {
            return userRepository
                    .findByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByCreatedAtDesc(term, term);
        }
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findUserById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    @Transactional
    public String toggleEnabled(Long id, String actingAdminEmail) {
        User user = requireUser(id);
        if (isSelf(user, actingAdminEmail)) {
            return null;
        }
        user.setEnabled(!user.isEnabled());
        userRepository.save(user);
        return user.isEnabled()
                ? "User account enabled successfully."
                : "User account disabled successfully.";
    }

    @Override
    @Transactional
    public String changeRole(Long id, Role newRole, String actingAdminEmail) {
        User user = requireUser(id);
        if (isSelf(user, actingAdminEmail)) {
            return null;
        }
        if (newRole == null) {
            return null;
        }
        if (newRole == user.getRole()) {
            return "Role is already set to " + newRole + ".";
        }
        user.setRole(newRole);
        userRepository.save(user);
        return "Role updated successfully.";
    }

    @Override
    @Transactional
    public String deleteUser(Long id, String actingAdminEmail) {
        User user = requireUser(id);
        if (isSelf(user, actingAdminEmail)) {
            return null;
        }
        userRepository.delete(user);
        return "User deleted successfully.";
    }

    private boolean isSelf(User user, String actingAdminEmail) {
        return user.getEmail().equalsIgnoreCase(actingAdminEmail);
    }

    private User requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found."));
    }
}