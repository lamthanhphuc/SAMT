package com.example.user_groupservice.mapper;

import com.example.user_groupservice.dto.response.UserResponse;
import com.example.user_groupservice.grpc.GetUserResponse;

import java.util.List;

public final class UserGrpcMapper {

    private UserGrpcMapper() {}

    public static UserResponse toUserResponse(GetUserResponse grpcUser) {
        if (grpcUser.getDeleted()) {
            throw new IllegalStateException("Cannot map deleted user");
        }

        return UserResponse.builder()
                .id(Long.parseLong(grpcUser.getUserId()))
                .email(grpcUser.getEmail())
                .fullName(grpcUser.getFullName())
                .status(grpcUser.getStatus().name()) // Assuming status is an enum in gRPC
                .roles(List.of(grpcUser.getRole().name()))
                .build();
    }
}
