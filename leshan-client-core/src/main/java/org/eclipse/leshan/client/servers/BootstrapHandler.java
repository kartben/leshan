/*******************************************************************************
 * Copyright (c) 2016 Sierra Wireless and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *     Sierra Wireless - initial API and implementation
 *******************************************************************************/
package org.eclipse.leshan.client.servers;

import static org.eclipse.leshan.client.util.LwM2mId.SECURITY_ID;
import static org.eclipse.leshan.client.util.LwM2mId.SERVER_ID;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.leshan.client.resource.LwM2mObjectEnabler;
import org.eclipse.leshan.core.request.BootstrapDeleteRequest;
import org.eclipse.leshan.core.request.BootstrapFinishRequest;
import org.eclipse.leshan.core.request.DeleteRequest;
import org.eclipse.leshan.core.request.Identity;
import org.eclipse.leshan.core.response.BootstrapDeleteResponse;
import org.eclipse.leshan.core.response.BootstrapFinishResponse;

public class BootstrapHandler {

    private AtomicBoolean bootstrapping = new AtomicBoolean(false);
    private CountDownLatch bootstrappingLatch = new CountDownLatch(1);

    private ServerInfo bootstrapServerInfo;
    private final Map<Integer, LwM2mObjectEnabler> objects;

    public BootstrapHandler(Map<Integer, LwM2mObjectEnabler> objectEnablers) {
        objects = objectEnablers;
    }

    public synchronized BootstrapFinishResponse finished(Identity identity, BootstrapFinishRequest finishedRequest) {
        if (bootstrapping()) {
            // only if the request is from the bootstrap server
            if (!isBootstrapServer(identity)) {
                return BootstrapFinishResponse.badRequest("not from a bootstrap server");
            }
            // TODO delete bootstrap server (see 5.2.5.2 Bootstrap Delete)

            bootstrappingLatch.countDown();
            return BootstrapFinishResponse.success();
        } else {
            return BootstrapFinishResponse.badRequest("no pending bootstrap session");
        }
    }

    public synchronized BootstrapDeleteResponse delete(Identity identity, BootstrapDeleteRequest deleteRequest) {
        if (bootstrapping()) {
            // Only if the request is from the bootstrap server
            if (!isBootstrapServer(identity)) {
                return BootstrapDeleteResponse.methodNotAllowed("not from a bootstrap server");
            }

            // TODO the spec say that delete on "/" should delete all the existing Object Instances - except LWM2M
            // Bootstrap Server Account, (see 5.2.5.2 Bootstrap Delete)
            // For now we only remove security and server object.

            // Delete all device management server
            LwM2mObjectEnabler serverObject = objects.get(SERVER_ID);
            for (Integer instanceId : serverObject.getAvailableInstanceIds()) {
                serverObject.delete(identity, new DeleteRequest(SERVER_ID, instanceId));
            }

            // Delete all security instance (except bootstrap one)
            // TODO do not delete boostrap server (see 5.2.5.2 Bootstrap Delete)
            LwM2mObjectEnabler securityObject = objects.get(SECURITY_ID);
            for (Integer instanceId : serverObject.getAvailableInstanceIds()) {
                securityObject.delete(identity, new DeleteRequest(SERVER_ID, instanceId));
            }

            return BootstrapDeleteResponse.success();
        } else {
            return BootstrapDeleteResponse.methodNotAllowed("no pending bootstrap session");
        }
    }

    public synchronized boolean tryToInitSession(ServerInfo bootstrapServerInfo) {
        this.bootstrapServerInfo = bootstrapServerInfo;
        boolean bootstrapping = this.bootstrapping.compareAndSet(false, true);
        if (bootstrapping)
            bootstrappingLatch = new CountDownLatch(1);
        return bootstrapping;
    }

    public boolean waitBoostrapFinished(long timeInSeconds) {
        try {
            return bootstrappingLatch.await(timeInSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    public synchronized void cancelSession() {
        bootstrapServerInfo = null;
        bootstrapping.set(false);
    }

    /**
     * @return <code>true</code> if the client is currently bootstrapping
     */
    public boolean bootstrapping() {
        return bootstrapping.get();
    }

    /**
     * @return <code>true</code> if the given request sender identity is the bootstrap server (same IP address)
     */
    private synchronized boolean isBootstrapServer(Identity identity) {
        if (bootstrapServerInfo == null) {
            return false;
        }

        return bootstrapServerInfo.getAddress().getAddress() != null
                && bootstrapServerInfo.getAddress().getAddress().equals(identity.getPeerAddress().getAddress());
    }
}
