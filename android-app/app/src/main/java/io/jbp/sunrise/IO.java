package io.jbp.sunrise;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Created by jbp on 07/03/2015.
 */
public class IO
{
    private static IO _instance = new IO();
    private DatagramWriter writer;

    public static IO getInstance()
    {
        return _instance;
    }

    private IO()
    {
        writer = new DatagramWriter();
        writer.start();
    }

    public void write(byte[] data)
    {
        writer.send(data);
    }

    public void drop()
    {
        writer.drop();
    }
}

class DatagramWriter extends Thread
{
    private static String TAG = "DatagramWriter";
    private static int PORT = 9999;

    private DatagramSocket socket;
    private InetAddress addr;
    private boolean connected = false;
    private LinkedBlockingQueue<byte[]> outgoing = new LinkedBlockingQueue<>();

    DatagramWriter()
    {
        try
        {
            socket = new DatagramSocket(PORT);
            socket.setBroadcast(true);
            socket.setSoTimeout(5000);
            socket.setReuseAddress(true);
        } catch (SocketException e) {
            Log.e(TAG, "cannot open datagram socket", e);
            throw new Error("networking broken", e);
        }
    }

    void send(byte[] data)
    {
        outgoing.add(data);
    }
    void drop() { outgoing.clear(); }

    // --- run on thread:

    public void run()
    {
        while (true)
        {
            try
            {
                if (connected)
                {
                    sendQueue();
                } else
                {
                    discover();
                }
                sleep(100);
            } catch (Exception e) {
                Log.e(TAG, "Error occurred", e);
                connected = false;
                sleep(1000);
            }
        }
    }

    private void sleep(int ms)
    {
        try
        {
            Thread.sleep(ms);
        } catch (InterruptedException e)
        {
        }
    }

    private void discover() throws IOException
    {
        byte[] data = "ping".getBytes();
        DatagramPacket p = new DatagramPacket(data, data.length, InetAddress.getByName("192.168.0.255"), PORT);
        socket.send(p);

        byte[] payload = new byte[32];
        DatagramPacket ack = new DatagramPacket(payload, payload.length);

        while (true)
        {
            socket.receive(ack);
            // ignore our own broadcast
            if (ack.getLength() == 4 &&
                payload[0] == 'p' &&
                payload[1] == 'i' &&
                payload[2] == 'n' &&
                payload[3] == 'g')
                continue;
            break;
        }

        String response = new String(ack.getData(), ack.getOffset(), ack.getLength(), "ASCII").trim();
        Log.v(TAG, "got ping response '" + response + "' from " + ack.getAddress().toString());

        if (!response.equals("ACK"))
        {
            throw new IOException("message not ackd");
        } else {
            connected = true;
            addr = ack.getAddress();
            Log.v(TAG, "found device at " + addr.toString());
        }
    }

    private void sendQueue() throws InterruptedException, IOException
    {
        byte[] data = outgoing.take();
        Log.v(TAG, "sending " + Arrays.toString(data));
        DatagramPacket p = new DatagramPacket(data, data.length, this.addr, PORT);
        socket.send(p);
    }
}