package com.phs.application.service;


import com.phs.application.entity.User;
import com.phs.application.model.dto.UserDTO;
import com.phs.application.model.request.ChangePasswordRequest;
import com.phs.application.model.request.CreateUserRequest;
import com.phs.application.model.request.UpdateProfileRequest;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public interface UserService {
    List<UserDTO> getListUsers();

    Page<User> adminListUserPages(String fullName, String phone, String email, Integer page);

    User createUser(CreateUserRequest createUserRequest);

    void changePassword(User user, ChangePasswordRequest changePasswordRequest);

    User updateProfile(User user, UpdateProfileRequest updateProfileRequest);
}
