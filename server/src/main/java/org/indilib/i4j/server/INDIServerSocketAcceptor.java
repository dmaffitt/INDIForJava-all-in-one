package org.indilib.i4j.server;

/*
 * #%L
 * INDI for Java Server Library
 * %%
 * Copyright (C) 2012 - 2014 indiforjava
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Lesser Public License for more details.
 *
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import org.indilib.i4j.Constants;
import org.indilib.i4j.protocol.api.INDIConnection;
import org.indilib.i4j.protocol.io.INDISocketConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;

/**
 * A basic indi protocol connection acceptor. it wil accept standard indi
 * clients connection to the server socket.
 *
 * @author Richard van Nieuwenhoven
 */
public abstract class INDIServerSocketAcceptor implements INDIServerAcceptor {

    /**
     * Logger to log to.
     */
    private static final Logger LOG = LoggerFactory.getLogger(INDIServerSocketAcceptor.class);

    /**
     * The port to which the Server listens.
     */
    private int listeningPort;

    /**
     * If <code>true</code> the mainThread will continue running.
     */
    private volatile boolean mainThreadRunning;

    /**
     * The socket to which the Server listens.
     */
    private volatile ServerSocket socket;

    /**
     * constructor for the basic server socket acceptor.
     */
    public INDIServerSocketAcceptor() {
        mainThreadRunning = false;
        listeningPort = 0;
    }

    /**
     * Gets the port to which the Server listens.
     *
     * @return The port to which the Server listens.
     */
    private int getListeningPort() {
        if (listeningPort <= 0) {
            listeningPort = getDefaultPort();
        }
        return listeningPort;
    }

    /**
     * @return the default port the server listens to.
     */
    protected int getDefaultPort() {
        return Constants.INDI_DEFAULT_PORT;
    }

    /**
     * The thread listens to the server socket and when a client connects, it is
     * added to the list of clients.
     */
    @Override
    public void run() {
        final Logger acceptorLog = acceptorLog();
        if (socket == null && !bind()) {
            return; // The thread will stop
        }
        ServerSocket serverSocket = socket;

        while (mainThreadRunning) {
            Socket clientSocket;

            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                // This is usually the escape point of the thread when the
                // server is stopped: expected then, an error otherwise.
                if (mainThreadRunning) {
                    acceptorLog.error("Server has stopped listening to new Client connections", e);
                } else {
                    acceptorLog.info("Stopped listening to port " + listeningPort);
                }
                mainThreadRunning = false;
                return; // The thread will stop
            }

            if (clientSocket != null) {
                INDIConnection clientConnection = createINDIConnection(clientSocket);
                if (!acceptClient(clientConnection)) {
                    try {
                        clientConnection.close();
                    } catch (IOException e) {
                        acceptorLog.warn("client close exception", e);
                    }
                    acceptorLog.info("Client " + clientSocket.getInetAddress() + " rejected");
                }
            }
        }
    }

    /**
     * create an indiconnection around the socket. other protocols may overwrite
     * this method in subclasses.
     *
     * @param clientSocket the client socket to connect to.
     * @return the indi connection
     */
    protected INDIConnection createINDIConnection(Socket clientSocket) {
        return new INDISocketConnection(clientSocket);
    }

    @Override
    public boolean isRunning() {
        return mainThreadRunning;
    }

    /**
     * @return the logger named after the concrete acceptor class, anonymous
     * subclasses use their superclass.
     */
    private Logger acceptorLog() {
        Class<?> loggerClass = getClass();
        while (loggerClass.getName().indexOf('$') >= 0) {
            loggerClass = loggerClass.getSuperclass();
        }
        return LoggerFactory.getLogger(loggerClass);
    }

    /**
     * open the server socket.
     *
     * @return true if the socket is listening.
     */
    private synchronized boolean bind() {
        try {
            // Reuse the address: a port a client connection just left in TIME_WAIT, or a server just
            // stopped, can be bound again at once instead of failing as busy for a minute.
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new java.net.InetSocketAddress(getListeningPort()));
        } catch (IOException e) {
            acceptorLog().error("Could not listen on port: " + listeningPort + " (maybe busy)");
            return false;
        }
        acceptorLog().info("Listening to port " + listeningPort);
        mainThreadRunning = true;
        return true;
    }

    /**
     * Binds the socket before returning, so host, port and running state are
     * valid as soon as start returns; the connections are accepted in a new
     * thread.
     */
    @Override
    public synchronized void start() {
        if (!mainThreadRunning && bind()) {
            Thread serverThread = new Thread(this, getName() + " acceptor " + listeningPort);
            serverThread.start();
        }
    }

    @Override
    public synchronized void close() {
        mainThreadRunning = false;
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException e) {
            LOG.warn("server port close exception", e);
        }
    }

    @Override
    public String getHost() {
        ServerSocket current = socket;
        return current == null ? null : current.getInetAddress().getHostName();
    }

    @Override
    public int getPort() {
        ServerSocket current = socket;
        return current == null ? getListeningPort() : current.getLocalPort();
    }

    @Override
    public String getName() {
        return "INDI";
    }

    @Override
    public void setArguments(Object... arguments) {
        if (arguments != null && arguments.length > 0 && arguments[0] != null) {
            try {
                listeningPort = Integer.parseInt(arguments[0].toString().trim());
            } catch (Exception e) {
                LOG.error("argument was not port integer", e);
            }
        }
    }

    @Override
    public boolean isLocalURL(URL url) {
        boolean hostEqual;
        boolean portEqual;
        String hostName = url.getHost();
        if (hostName == null || hostName.isEmpty()) {
            hostEqual = true;
        } else {
            try {
                InetAddress inetAdress = InetAddress.getByName(hostName);
                hostEqual = inetAdress.isAnyLocalAddress() || inetAdress.isLoopbackAddress();
            } catch (UnknownHostException e) {
                LOG.warn("host name lookup failed " + hostName, e);
                hostEqual = false;
            }
        }
        int port = url.getPort();
        if (port <= 0) {
            port = url.getDefaultPort();
        }
        portEqual = getListeningPort() == port;
        return portEqual && hostEqual;
    }

}
