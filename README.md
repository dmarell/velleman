## velleman

``velleman`` is a library containing support classes for Velleman USB I/O devices.

The library is packaged as an OSGi bundle.

### Release notes

* Version 1.0.7 - 2015-07-07
 Moved to Github.
* Version 1.0.6 - 2014-02-08
 * Java 7
 * Changed pom versioning mechanism.
 * Extended site information.
 * Updated versions of dependencies
* Version 1.0 - 2011-10-26
  First version.

### Supported devices

In this version a single device is supported: [The K8055 USB Experimental Board](http://www.velleman.eu/products/view/?id=351346)

The K8055 is interfaced using libusb 1.0 via the OSGi bundle [libusb10j](http://github.com/dmarell/libusb10j).

The class ``SynchronousK8055`` utilizes the synchronous functions of libusb. In some cases it may be
desirable to use asynchronous functions instead in order to get rid of I/O waiting. However, because this
waiting time is small in relation to the response time of the I/O device and, especially, if device access is
executed in a separate thread, the negative effects of synchronous I/O may be acceptable.

### Maven usage

```
<repositories>
  <repository>
    <id>marell</id>
    <url>http://marell.se/nexus/content/repositories/releases/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>se.marell.iodevices</groupId>
    <artifactId>velleman</artifactId>
    <version>1.0.6</version>
  </dependency>
</dependencies>
```

### K8055 installation

This has been tested on ubuntu 10.04, 12.04 and 14.04 LTS and Raspian with kernel 3.18 and 4.0.6.

First, verify that your device is recognized:

```
$ tail -f /var/log/syslog
```

Connect the K8055. The output the log should be something similar to this:

```
...
Oct 26 00:59:45 spitfire kernel: [1549618.763141] usb 2-6.2: new low speed USB device using ehci_hcd and address 21
Oct 26 00:59:45 spitfire kernel: [1549618.875830] usb 2-6.2: configuration #1 chosen from 1 choice
Oct 26 00:59:45 spitfire kernel: [1549618.878825] generic-usb 0003:10CF:5500.0017: hiddev97,hidraw3: USB HID v1.00 Device [Velleman  USB K8055] on usb-0000:00:1d.7-6.2/input0
...
```

Arrange for setting permissions on the device in order to be able to open it:

```
$ sudo vi /etc/udev/rules.d/95-libusb.rules
...
SUBSYSTEM=="usb",ACTION=="add",ATTR{idVendor}=="10cf",ATTR{idProduct}=="5500",GROUP="usb"
...
$ sudo udevadm control --reload-rules
```

Create a group "usb" and place yourself in it:

```
$ sudo groupadd usb
$ sudo usermod -a -G usb yourusername
```

Logout and login again in order for the group membership to take effect.

### K8055 example usage

Init the libusb, create a device and set an output:

```
UsbSystem us = new LibUsbSystem(false, 0);
SynchronousK8055 k = new SynchronousK8055(us, 0);
k.setDo(1, true);
k.poll();
```

A more comprehensive example:

```
public class SynchronousK8055Demo {

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
```

