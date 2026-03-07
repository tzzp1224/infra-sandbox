package com.demo.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class AgentClientMain {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext().build();
        AgentServiceGrpc.AgentServiceBlockingStub stub = AgentServiceGrpc.newBlockingStub(channel);

        AnalyzeResponse response = stub.analyzeText(AnalyzeRequest.newBuilder().setText("hello token").build());

        // Protobuf 响应是强类型对象，可直接 response.getLength()；若用 JSON 通常要先取字符串再手动解析键和值。
        System.out.println("status=" + response.getStatus() + ", length=" + response.getLength());

        channel.shutdown();
    }
}
