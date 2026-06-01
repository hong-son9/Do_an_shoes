package com.phs.application.service.impl;

import com.phs.application.exception.BadRequestException;
import com.phs.application.repository.UserRepository;
import com.phs.application.entity.User;
import com.phs.application.model.dto.UserDTO;
import com.phs.application.model.mapper.UserMapper;
import com.phs.application.model.request.ChangePasswordRequest;
import com.phs.application.model.request.CreateUserRequest;
import com.phs.application.model.request.UpdateProfileRequest;
import com.phs.application.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.phs.application.config.Contant.LIMIT_USER;

@Component
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public List<UserDTO> getListUsers() {
        List<User> users = userRepository.findAll();
        List<UserDTO> userDTOS = new ArrayList<>();
        for (User user : users) {
            userDTOS.add(UserMapper.toUserDTO(user));
        }
        return userDTOS;
    }

    @Override
    public Page<User> adminListUserPages(String fullName, String phone, String email, Integer page) {
        page--;
        if (page < 0) {
            page = 0;
        }
        Pageable pageable = PageRequest.of(page, LIMIT_USER, Sort.by("created_at").descending());
        return userRepository.adminListUserPages(fullName, phone, email, pageable);
    }

    @Override
    public User createUser(CreateUserRequest createUserRequest) {
        User user = userRepository.findByEmail(createUserRequest.getEmail());
        if (user != null) {
            throw new BadRequestException("Email đã tồn tại trong hệ thống. Vui lòng sử dụng email khác!");
        }
        user = UserMapper.toUser(createUserRequest);
        userRepository.save(user);
        return user;
    }

    @Override
    public boolean existsByEmail(String email) {
        if (email == null) return false;
        return userRepository.findByEmail(email) != null;
    }

    @Override
    public User findOrCreateOAuthUser(String email, String fullName) {
        if (email == null || email.trim().isEmpty()) {
            throw new BadRequestException("Email từ Google không hợp lệ");
        }
        String normalized = email.trim();
        User existing = userRepository.findByEmail(normalized);
        if (existing != null) {
            return existing;
        }
        // Tao user moi voi mat khau random (user chi login qua Google)
        User user = new User();
        user.setEmail(normalized);
        user.setFullName(fullName != null && !fullName.isEmpty() ? fullName : normalized.split("@")[0]);
        String randomPassword = java.util.UUID.randomUUID().toString();
        user.setPassword(BCrypt.hashpw(randomPassword, BCrypt.gensalt(12)));
        user.setRoles(new java.util.ArrayList<>(java.util.Collections.singletonList("USER")));
        user.setStatus(true);
        user.setCreatedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        userRepository.save(user);
        return user;
    }

    @Override
    public void resetPasswordByEmail(String email, String newPassword) {
        if (email == null || newPassword == null) {
            throw new BadRequestException("Thông tin không hợp lệ");
        }
        // KHONG toLowerCase — phai khop chinh xac voi luc dang ky
        User user = userRepository.findByEmail(email.trim());
        if (user == null) {
            throw new BadRequestException("Tài khoản không tồn tại");
        }
        String hash = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
        user.setPassword(hash);
        userRepository.save(user);
    }

    @Override
    public void changePassword(User user, ChangePasswordRequest changePasswordRequest) {
        //Kiểm tra mật khẩu
        if (!BCrypt.checkpw(changePasswordRequest.getOldPassword(), user.getPassword())) {
            throw new BadRequestException("Mật khẩu cũ không chính xác");
        }

        String hash = BCrypt.hashpw(changePasswordRequest.getNewPassword(), BCrypt.gensalt(12));
        user.setPassword(hash);
        userRepository.save(user);
    }

    @Override
    public User updateProfile(User user, UpdateProfileRequest updateProfileRequest) {
        user.setFullName(updateProfileRequest.getFullName());
        user.setPhone(updateProfileRequest.getPhone());
        user.setAddress(updateProfileRequest.getAddress());

        return userRepository.save(user);
    }

    @Override
    public User toggleUserStatus(long id, long currentAdminId) {
        if (id == currentAdminId) {
            throw new BadRequestException("Không thể tự khóa tài khoản của chính mình");
        }
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Tài khoản không tồn tại"));
        // Chan khoa tai khoan admin de tranh lockout he thong
        if (user.getRoles() != null && user.getRoles().contains("ADMIN")) {
            throw new BadRequestException("Không thể khóa tài khoản quản trị viên khác");
        }
        user.setStatus(!user.isStatus());
        user.setModifiedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        return userRepository.save(user);
    }
}
