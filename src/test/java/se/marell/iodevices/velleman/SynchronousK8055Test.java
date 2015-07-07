/*
 * Created by Daniel Marell 2011-09-18 17:28
 */
package se.marell.iodevices.velleman;

import org.junit.Test;
import se.marell.libusb.LibUsbSystem;
import se.marell.libusb.UsbSystem;

public class SynchronousK8055Test {
  @Test
  public void test() throws Exception {
    try {
      UsbSystem us = new LibUsbSystem(false, 0);
      SynchronousK8055 k = new SynchronousK8055(us, 0);
      k.setDo(1, true);
      k.poll();
    } catch (UnsatisfiedLinkError e) {
      e.printStackTrace();
      return;
    }
  }

  public static void main(String[] args) {
    UsbSystem us = new LibUsbSystem(false, 3);
    SynchronousK8055 k = new SynchronousK8055(us, 0);
    byte doValue = 0;
    int diValue = -1;
    while (true) {
      k.setDo(3, ++doValue % 8 == 0);
      byte v = k.getDiByte();
      if (v != diValue) {
        diValue = v;
        System.out.println("dinPort=" + getDiPortString(k) + ",dinBytes=" + getDiByteString(k));
      }
      if (!k.poll()) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
      }
    }
  }

  private static String getDiPortString(SynchronousK8055 k) {
    String s = "";
    for (int i = 5; i >= 1; --i) {
      s += k.getDi(i) ? "1" : "0";
    }
    return s;
  }

  private static String getDiByteString(SynchronousK8055 k) {
    byte b = k.getDiByte();
    String s = "";
    for (int i = 7; i >= 0; --i) {
      s += (b & (1 << i)) != 0 ? "1" : "0";
    }
    return s;
  }
}
