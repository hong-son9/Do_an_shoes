package com.phs.application.service;

import com.phs.application.entity.Comment;
import com.phs.application.model.request.CreateCommentPostRequest;
import com.phs.application.model.request.CreateCommentProductRequest;
import org.springframework.stereotype.Service;

@Service
public interface CommentService {
    Comment createCommentPost(CreateCommentPostRequest createCommentPostRequest,long userId);
    Comment createCommentProduct(CreateCommentProductRequest createCommentProductRequest, long userId);
}
