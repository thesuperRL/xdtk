package com.google.xrinput;

public abstract class Transceiver {
    protected String TAG;
    protected volatile boolean running;
    protected CommunicationHandler communicationHandler;

    public void sendData(String data){

    }

    public void close() {

    }

    // returns whether it's running
    public boolean isRunning() {
        return running;
    }
}
