/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package studio.driver.usb;

import java.util.concurrent.CompletableFuture;

import org.usb4java.Context;
import org.usb4java.Device;
import org.usb4java.DeviceDescriptor;
import org.usb4java.DeviceList;
import org.usb4java.LibUsb;
import org.usb4java.LibUsbException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import studio.driver.event.DevicePluggedListener;
import studio.driver.event.DeviceUnpluggedListener;
import studio.driver.model.UsbDeviceFirmware;
import studio.driver.model.UsbDeviceVersion;

public class LibUsbActivePollingWorker implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LibUsbActivePollingWorker.class);

    private final Context context;
    private final UsbDeviceVersion deviceVersion;
    private final DevicePluggedListener pluggedlistener;
    private final DeviceUnpluggedListener unpluggedlistener;
    private Device device = null;

    public LibUsbActivePollingWorker(Context context, UsbDeviceVersion deviceVersion,
            DevicePluggedListener pluggedlistener, DeviceUnpluggedListener unpluggedlistener) {
        this.context = context;
        this.deviceVersion = deviceVersion;
        this.pluggedlistener = pluggedlistener;
        this.unpluggedlistener = unpluggedlistener;
    }

    @Override
    public void run() {
        LOGGER.debug("Active polling for USB device");

        // List available devices
        DeviceList devices = new DeviceList();
        int result = LibUsb.getDeviceList(context, devices);
        if (result < 0) {
            throw new LibUsbException("Unable to get libusb device list", result);
        }
        try {
            // Iterate over all devices and scan for the right one
            Device found = null;
            for (Device d : devices) {
                DeviceDescriptor desc = new DeviceDescriptor();
                result = LibUsb.getDeviceDescriptor(d, desc);
                if (result != LibUsb.SUCCESS) {
                    throw new LibUsbException("Unable to read libusb device descriptor", result);
                }
                if ((deviceVersion.isV1() && UsbDeviceFirmware.isV1(desc))
                        || (deviceVersion.isV2() && UsbDeviceFirmware.isV2(desc))) {
                    found = d;
                    break;
                }
            }
            // Fire plugged / unplugged events
            if (found != null && device == null) {
                LOGGER.info("Active polling found a new device. Firing event.");
                device = found;
                CompletableFuture.runAsync(() -> pluggedlistener.onDevicePlugged(device));
            } else if (found == null && device != null) {
                LOGGER.info("Active polling lost the device. Firing event.");
                CompletableFuture.runAsync(() -> unpluggedlistener.onDeviceUnplugged(device));
                device = null;
            }
        } finally {
            // Ensure the allocated device list is freed
            LibUsb.freeDeviceList(devices, false); // Do NOT unref devices
        }
    }
}
