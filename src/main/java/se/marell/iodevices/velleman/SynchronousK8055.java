/*
 * Copyright (c) 2011 Daniel Marell
 * All rights reserved.
 *
 * Permission is hereby granted, free  of charge, to any person obtaining
 * a  copy  of this  software  and  associated  documentation files  (the
 * "Software"), to  deal in  the Software without  restriction, including
 * without limitation  the rights to  use, copy, modify,  merge, publish,
 * distribute,  sublicense, and/or sell  copies of  the Software,  and to
 * permit persons to whom the Software  is furnished to do so, subject to
 * the following conditions:
 *
 * The  above  copyright  notice  and  this permission  notice  shall  be
 * included in all copies or substantial portions of the Software.
 *
 * THE  SOFTWARE IS  PROVIDED  "AS  IS", WITHOUT  WARRANTY  OF ANY  KIND,
 * EXPRESS OR  IMPLIED, INCLUDING  BUT NOT LIMITED  TO THE  WARRANTIES OF
 * MERCHANTABILITY,    FITNESS    FOR    A   PARTICULAR    PURPOSE    AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE,  ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package se.marell.iodevices.velleman;

import se.marell.libusb.AbstractSynchronousUsbDevice;
import se.marell.libusb.LibUsbException;
import se.marell.libusb.UsbSystem;

import java.util.Arrays;

/**
 * Driver for USB board Velleman K8055.
 * -5 digital inputs (0=ground, 1=open)
 * -2 Analog inputs (0..+5V)
 * -8 Digital outputs (open collector, 100mA)
 * -2 Analog outputs (0..+5V output resistance 1k5, PWM open collector outputs)
 * Conversion time 20 ms per command.
 * Power requirement 70mA through USB
 * Digital output 8 seems to flash at startup, so I suppose that this output shall be reserved
 * as a general status LED and not to be connected to any controlled device.
 *
 * The driver communicates with the USB board using synchronous commands.
 *
 * Run in a separate thread in a loop:
 * <pre>
 * forever {
 *   k8055.get inputs
 *   k8055.set outputs
 *   k8055.poll
 * }
 *
 * Output packet format
 *
 * +---+---+---+---+---+---+---+---+
 * |CMD|DIG|An1|An2|Rs1|Rs2|Dbv|Dbv|
 * +---+---+---+---+---+---+---+---+
 * CMD = Command
 * DIG = Digital output bitmask
 * An1 = Analog output 1 value, 0-255
 * An2 = Analog output 2 value, 0-255
 * Rs1 = Reset counter 1, command 3
 * Rs2 = Reset counter 3, command 4
 * Dbv = Debounce value for counter 1 and 2, command 1 and 2
 * </pre>
 */
public class SynchronousK8055 extends AbstractSynchronousUsbDevice {
    private static final int VENDOR = 0x10cf;
    private static final int PRODUCT = 0x5500;

    private byte doValue;
    private byte ao1Value;
    private byte ao2Value;

    private byte diValue;
    private byte statusValue;
    private byte ai1Value;
    private byte ai2Value;
    private int c1Value;
    private int c2Value;

    private byte[] dataBuffer = new byte[8];
    private int readTimeout = DEFAULT_RW_TIMEOUT_MS;
    private int writeTimeout = DEFAULT_RW_TIMEOUT_MS;

    /**
     * @param us           The UsbSystem
     * @param deviceNumber 0 for first board, 1 for 2nd etc.
     */
    public SynchronousK8055(UsbSystem us, int deviceNumber) {
        super(us, deviceNumber);
    }

    /**
     * Get device status.
     *
     * @return Device status
     */
    public byte getStatus() {
        return statusValue;
    }

    /**
     * Control digital outputs.
     *
     * @param status LSB=out1, MSB=out8
     */

    /**
     * Set a digital output port.
     *
     * @param port   Port 1..7
     * @param status true=on=1
     */
    public void setDo(int port, boolean status) {
        if (status) {
            doValue |= (1 << (port - 1));
        } else {
            doValue &= ~(1 << (port - 1));
        }
    }

    /**
     * Read status for a digital input.
     *
     * @param port Port 1..5
     * @return Port status
     */
    public boolean getDi(int port) {
        int bitno;
        switch (port) {
            case 1:
                bitno = 4;
                break;
            case 2:
                bitno = 5;
                break;
            case 3:
                bitno = 0;
                break;
            case 4:
                bitno = 6;
                break;
            case 5:
                bitno = 7;
                break;
            default:
                throw new IllegalArgumentException("Port must be 1..5");
        }
        return (diValue & (1 << (bitno))) != 0;
    }

    public byte getDiByte() {
        return diValue;
    }

    /**
     * Set Analog output 1.
     *
     * @param value 0=0V, 255=+5V or 0-100% PWM
     */
    public void setAo1(int value) {
        ao1Value = (byte) value;
    }

    /**
     * Set Analog output 2.
     *
     * @param value 0=0V, 255=+5V or 0-100% PWM
     */
    public void setAo2(int value) {
        ao2Value = (byte) value;
    }

    /**
     * Get value for analog input 1.
     *
     * @return 0=0V, 255=+5V
     */
    public int getAi1() {
        return (int) ai1Value & 0xff;
    }

    /**
     * Get value for analog input 2.
     *
     * @return 0=0V, 255=+5V
     */
    public int getAi2() {
        return (int) ai2Value & 0xff;
    }

    /**
     * Get value for counter 1.
     *
     * @return Value of counter 1
     */
    public int getC1Value() {
        return (int) c1Value & 0xff;
    }

    /**
     * Get value for counter 2.
     *
     * @return Value of counter 2
     */
    public int getC2Value() {
        return (int) c2Value & 0xff;
    }

    /**
     * Get current read timeout in ms.
     *
     * @return timeout in ms, 0=no timeout
     */
    public int getReadTimeout() {
        return readTimeout;
    }

    /**
     * Set new read timeout in ms.
     *
     * @param readTimeout Timeout in ms, 0=no timeout
     */
    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    /**
     * Get current write timeout in ms.
     *
     * @return timeout in ms, 0=no timeout
     */
    public int getWriteTimeout() {
        return writeTimeout;
    }

    /**
     * Set new write timeout in ms.
     *
     * @param writeTimeout Timeout in ms, 0=no timeout
     */
    public void setWriteTimeout(int writeTimeout) {
        this.writeTimeout = writeTimeout;
    }

    /**
     * Send value of outputs to K8055 and read inputs from K8055. Try to reconnect if device is disconnected.
     *
     * @return true if device is connected
     */
    public boolean poll() {
        if (!connectDevice()) {
            return false;
        }
        if (!writeAnalogDigital()) {
            return false;
        }
        if (!readDevice()) {
            return false;
        }
        return true;
    }

    public boolean writeAnalogDigital() {
        if (!internalWriteAnalogDigital()) {
            device.close();
            device = null;
            return false;
        }
        return true;
    }

    public boolean readDevice() {
        if (!internalReadDevice()) {
            device.close();
            device = null;
            return false;
        }
        return true;
    }

    private boolean connectDevice() {
        if (device == null) {
            try {
                device = getUsbDevice(VENDOR, PRODUCT, deviceNumber);
                if (device != null) {
                    log.info("Connected device vendor={} product={} deviceNumber={}", new Object[]{VENDOR, PRODUCT, deviceNumber});
                }
            } catch (LibUsbException e) {
                log.warn("Failed connecting device {} vendor={} product={} deviceNumber={} cause={}",
                        new Object[]{VENDOR, PRODUCT, deviceNumber, e.getMessage()});
                return false;
            }
            if (device == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * cmd 5, Set analog/digital
     * +---+---+---+---+---+---+---+---+
     * | 5 |DIG|An1|An2|   |   |   |   |
     * +---+---+---+---+---+---+---+---+
     *
     * @return true if write succeeded
     */
    private boolean internalWriteAnalogDigital() {
        Arrays.fill(dataBuffer, (byte) 0);
        dataBuffer[0] = 0x05;
        dataBuffer[1] = doValue;
        dataBuffer[2] = ao1Value;
        dataBuffer[3] = ao2Value;
        try {
            device.interrupt_write(0x1, dataBuffer, writeTimeout);
            log.trace("wrote {}", dataBuffer);
        } catch (LibUsbException e) {
            log.warn("write failed:" + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Reset command (0):
     * Cmd 0, Reset ??
     *
     * @return true if command was sent successfully
     */
    public boolean commandReset() {
        throw new IllegalArgumentException("Not implemented"); // todo implement
    }

    /**
     * Cmd 1, Set debounce Counter 1
     * +---+---+---+---+---+---+---+---+
     * |CMD|   |   |   |   |   |Dbv|   |
     * +---+---+---+---+---+---+---+---+
     *
     * @return true if command was sent successfully
     */
    public boolean commandSetDebounceCounter1() {
        throw new IllegalArgumentException("Not implemented"); // todo implement
    }

    /**
     * Cmd 2, Set debounce Counter 2
     * +---+---+---+---+---+---+---+---+
     * |CMD|   |   |   |   |   |   |Dbv|
     * +---+---+---+---+---+---+---+---+
     *
     * @return true if command was sent successfully
     */
    public boolean commandSetDebounceCounter2() {
        throw new IllegalArgumentException("Not implemented"); // todo implement
    }

    /**
     * Cmd 3, Reset counter 1
     * +---+---+---+---+---+---+---+---+
     * | 3 |   |   |   | 00|   |   |   |
     * +---+---+---+---+---+---+---+---+
     *
     * @return true if command was sent successfully
     */
    public boolean commandResetCounter1() {
        throw new IllegalArgumentException("Not implemented"); // todo implement
    }

    /**
     * Cmd 4, Reset counter 2
     * +---+---+---+---+---+---+---+---+
     * | 4 |   |   |   |   | 00|   |   |
     * +---+---+---+---+---+---+---+---+
     *
     * @return true if command was sent successfully
     */
    public boolean commandResetCounter2() {
        throw new IllegalArgumentException("Not implemented"); // todo implement
    }

    /**
     * Input packet format
     * <p/>
     * +---+---+---+---+---+---+---+---+
     * |DIn|Sta|A1 |A2 |   C1  |   C2  |
     * +---+---+---+---+---+---+---+---+
     * DIn = Digital input in high nibble, except for input 3 in 0x01
     * Sta = Status, Board number + 1
     * A1  = Analog input 1, 0-255
     * A2  = Analog input 2, 0-255
     * C1  = Counter 1, 16 bits (lsb)
     * C2  = Counter 2, 16 bits (lsb)
     *
     * @return true if read succeeded
     */
    private boolean internalReadDevice() {
        Arrays.fill(dataBuffer, (byte) 0);
        try {
            int n = device.interrupt_read(0x81, dataBuffer, readTimeout);
            log.trace("read {} bytes: {}", n, dataBuffer);
            if (n != dataBuffer.length) {
                log.warn("read: Expected {} bytes but got {}", dataBuffer.length, n);
                return false;
            }
        } catch (LibUsbException e) {
            log.warn("read failed:" + e.getClass().getSimpleName() + ":" + e.getMessage());
            return false;
        }
        diValue = dataBuffer[0];
        statusValue = dataBuffer[1];
        ai1Value = dataBuffer[2];
        ai2Value = dataBuffer[3];
        c1Value = (((int) dataBuffer[4] & 0xff) << 8) | ((int) dataBuffer[5] & 0xff);
        c2Value = (((int) dataBuffer[6] & 0xff) << 8) | ((int) dataBuffer[7] & 0xff);
        return true;
    }
}
