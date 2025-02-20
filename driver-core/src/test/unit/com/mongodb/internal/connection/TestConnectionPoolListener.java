/*
 * Copyright 2008-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mongodb.internal.connection;

import com.mongodb.event.ConnectionCheckOutFailedEvent;
import com.mongodb.event.ConnectionCheckOutStartedEvent;
import com.mongodb.event.ConnectionCheckedInEvent;
import com.mongodb.event.ConnectionCheckedOutEvent;
import com.mongodb.event.ConnectionClosedEvent;
import com.mongodb.event.ConnectionCreatedEvent;
import com.mongodb.event.ConnectionPoolClearedEvent;
import com.mongodb.event.ConnectionPoolClosedEvent;
import com.mongodb.event.ConnectionPoolCreatedEvent;
import com.mongodb.event.ConnectionPoolListener;
import com.mongodb.event.ConnectionPoolReadyEvent;
import com.mongodb.event.ConnectionReadyEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("deprecation")
public class TestConnectionPoolListener implements ConnectionPoolListener {

    private final Set<String> eventTypes;

    private final List<Object> events = new ArrayList<Object>();
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private volatile Class<?> waitingForEventClass;
    private volatile int waitingForEventCount;
    private final AtomicInteger numConnectionsCheckedOut = new AtomicInteger();


    public TestConnectionPoolListener() {
        this(Arrays.asList("poolCreatedEvent", "poolReadyEvent", "poolClearedEvent", "poolClosedEvent", "connectionCreatedEvent",
                "connectionReadyEvent", "connectionClosedEvent", "connectionCheckOutStartedEvent", "connectionCheckOutFailedEvent",
                "connectionCheckedOutEvent", "connectionCheckedInEvent",
                // These are deprecated, but still used by some tests
                "poolOpenedEvent", "connectionAddedEvent", "connectionRemovedEvent"));
    }

    public TestConnectionPoolListener(final List<String> eventTypes) {
        this.eventTypes = new HashSet<>(eventTypes);
    }

    public List<Object> getEvents() {
        lock.lock();
        try {
            return new ArrayList<Object>(events);
        } finally {
            lock.unlock();
        }
    }

    public <T> int countEvents(final Class<T> eventClass) {
        int eventCount = 0;
        for (Object event : getEvents()) {
            if (event.getClass().equals(eventClass)) {
                eventCount++;
            }
        }
        return eventCount;
    }

    public <T> void waitForEvent(final Class<T> eventClass, final int count, final long time, final TimeUnit unit)
            throws InterruptedException, TimeoutException {
        lock.lock();
        try {
            if (waitingForEventClass != null) {
                throw new IllegalStateException("Already waiting for events of class " + waitingForEventClass);
            }
            waitingForEventClass = eventClass;
            waitingForEventCount = count;
            if (containsEvent(eventClass, count)) {
                return;
            }
            if (!condition.await(time, unit)) {
                throw new TimeoutException("Timed out waiting for " + count + " events of type " + eventClass
                        + ". The count after timing out is " + countEvents(eventClass));
            }
        } finally {
            waitingForEventClass = null;
            lock.unlock();
        }
    }

    private <T> boolean containsEvent(final Class<T> eventClass, final int expectedEventCount) {
        return countEvents(eventClass) >= expectedEventCount;
    }

    private void addEvent(final Object event) {
        lock.lock();
        try {
            events.add(event);
            if (containsEvent(waitingForEventClass, waitingForEventCount)) {
                if (waitingForEventClass != null) {
                    waitingForEventClass = null;
                    condition.signalAll();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void connectionPoolCreated(final ConnectionPoolCreatedEvent event) {
        if (eventTypes.contains("poolCreatedEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionPoolOpened(final com.mongodb.event.ConnectionPoolOpenedEvent event) {
        if (eventTypes.contains("poolOpenedEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionPoolCleared(final ConnectionPoolClearedEvent event) {
        if (eventTypes.contains("poolClearedEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionPoolReady(final ConnectionPoolReadyEvent event) {
        if (eventTypes.contains("poolReadyEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionPoolClosed(final ConnectionPoolClosedEvent event) {
        if (eventTypes.contains("poolClosedEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionCheckOutStarted(final ConnectionCheckOutStartedEvent event) {
        if (eventTypes.contains("connectionCheckOutStartedEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionCheckedOut(final ConnectionCheckedOutEvent event) {
        numConnectionsCheckedOut.incrementAndGet();
        if (eventTypes.contains("connectionCheckedOutEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionCheckOutFailed(final ConnectionCheckOutFailedEvent event) {
        if (eventTypes.contains("connectionCheckOutFailedEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionCheckedIn(final ConnectionCheckedInEvent event) {
        numConnectionsCheckedOut.decrementAndGet();
        if (eventTypes.contains("connectionCheckedInEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionCreated(final ConnectionCreatedEvent event) {
        if (eventTypes.contains("connectionCreatedEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionAdded(final com.mongodb.event.ConnectionAddedEvent event) {
        if (eventTypes.contains("connectionAddedEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionRemoved(final com.mongodb.event.ConnectionRemovedEvent event) {
        if (eventTypes.contains("connectionRemovedEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionReady(final ConnectionReadyEvent event) {
        if (eventTypes.contains("connectionReadyEvent")) {
            addEvent(event);
        }
    }

    @Override
    public void connectionClosed(final ConnectionClosedEvent event) {
        if (eventTypes.contains("connectionClosedEvent")) {
            addEvent(event);
        }
    }

    public int getNumConnectionsCheckedOut() {
        return numConnectionsCheckedOut.get();
    }
}
