from concurrent import futures

import grpc

import agent_service_pb2
import agent_service_pb2_grpc


class AgentService(agent_service_pb2_grpc.AgentServiceServicer):
    def AnalyzeText(self, request, context):
        # Protobuf 会把字段按编号进行二进制编码，网络包比 JSON 更小，反序列化更快，且字段类型在编译期已固定。
        return agent_service_pb2.AnalyzeResponse(length=len(request.text), status="OK")


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=2))
    agent_service_pb2_grpc.add_AgentServiceServicer_to_server(AgentService(), server)
    server.add_insecure_port("[::]:50051")
    server.start()
    print("Python gRPC server running on 50051")
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
