/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.inbound.endpoint.protocol.grpc;

import com.google.protobuf.Empty;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.apache.log4j.Logger;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundRequestProcessor;
import org.wso2.carbon.inbound.endpoint.protocol.grpc.util.Event;
import org.wso2.carbon.inbound.endpoint.protocol.grpc.util.EventServiceGrpc;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class InboundGrpcListener implements InboundRequestProcessor {
    private int port;
    private GRPCInjectHandler injectHandler;
    private static final Logger log = Logger.getLogger(InboundGrpcListener.class.getName());
    private Server server;

    public InboundGrpcListener(InboundProcessorParams params) {
        String injectingSeq = params.getInjectingSeq();
        String onErrorSeq = params.getOnErrorSeq();
        SynapseEnvironment synapseEnvironment = params.getSynapseEnvironment();
        String portParam = params.getProperties().getProperty(InboundGrpcConstants.INBOUND_ENDPOINT_PARAMETER_GRPC_PORT);
        try {
            port = Integer.parseInt(portParam);
        } catch (NumberFormatException e) {
            port = 8888;
        }
        injectHandler = new GRPCInjectHandler(injectingSeq, onErrorSeq, false, synapseEnvironment);
    }

    public void init() {
        try {
            this.start();
        } catch (IOException e) {
            throw new SynapseException("IOException when starting gRPC server: " + e.getMessage(), e);
        }
    }

    public void destroy() {
        try {
            this.stop();
        } catch (InterruptedException e) {
            throw new SynapseException("Failed to stop gRPC server: " +e.getMessage());
        }
    }

    public void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("gRPC Listener Server already started");
        }
        server = ServerBuilder.forPort(port).addService(new EventServiceGrpc.EventServiceImplBase() {
            @Override
            public void process(Event request, StreamObserver<Event> responseObserver) {
                log.debug("Event received for gRPC Listener process method");
                injectHandler.invokeProcess(request, responseObserver);
            }

            @Override
            public void consume(Event request, StreamObserver<Empty> responseObserver) {
                log.debug("Event received for gRPC Listener consume method");
                injectHandler.invokeConsume(request, responseObserver);
                responseObserver.onNext(Empty.getDefaultInstance());
                responseObserver.onCompleted();
            }
        }).build();
        server.start();
        log.debug("gRPC Listener Server started");
    }

    public void stop() throws InterruptedException {
        Server s = server;
        if (s == null) {
            throw new IllegalStateException("gRPC Listener Server is already stopped");
        }
        server = null;
        s.shutdown();
        if (s.awaitTermination(1, TimeUnit.SECONDS)) {
            log.debug("gRPC Listener Server stopped");
            return;
        }
        s.shutdownNow();
        if (s.awaitTermination(1, TimeUnit.SECONDS)) {
            return;
        }
        throw new RuntimeException("Unable to shutdown gRPC Listener Server");
    }
}
