/*
 *  Copyright 2019-2020 Zheng Jie
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package me.zhengjie.modules.system.service.impl;

import cn.hutool.core.date.BetweenFormatter.Level;
import cn.hutool.core.date.DateUtil;
import me.zhengjie.modules.system.service.MonitorService;
import me.zhengjie.utils.FileUtils;
import me.zhengjie.utils.StringUtils;
import me.zhengjie.utils.enums.Constants;
import org.springframework.stereotype.Service;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.VirtualMemory;
import oshi.software.os.FileSystem;
import oshi.software.os.OSFileStore;
import oshi.software.os.OperatingSystem;
import oshi.util.FormatUtil;
import oshi.util.Util;

import java.lang.management.ManagementFactory;
import java.text.DecimalFormat;
import java.util.*;

import static me.zhengjie.utils.enums.Constants.*;

/**
* @author Zheng Jie
* @date 2020-05-02
*/
@Service
public class MonitorServiceImpl implements MonitorService {

    private final DecimalFormat df = new DecimalFormat("0.00");

    @Override
    public Map<String,Object> getServers(){
        Map<String, Object> resultMap = new LinkedHashMap<>(8);
        try {
            SystemInfo si = new SystemInfo();
            OperatingSystem os = si.getOperatingSystem();
            HardwareAbstractionLayer hal = si.getHardware();
            // 系统信息
            resultMap.put(SYS, getSystemInfo(os));
            // cpu 信息
            resultMap.put(CPU, getCpuInfo(hal.getProcessor()));
            // 内存信息
            resultMap.put(MEMORY, getMemoryInfo(hal.getMemory()));
            // 交换区信息
            resultMap.put(SWAP, getSwapInfo(hal.getMemory()));
            // 磁盘
            resultMap.put(DISK, getDiskInfo(os));
            resultMap.put(TIME, DateUtil.format(new Date(), "HH:mm:ss"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultMap;
    }

    /**
     * 获取磁盘信息
     * @return /
     */
    private Map<String,Object> getDiskInfo(OperatingSystem os) {
        Map<String,Object> diskInfo = new LinkedHashMap<>();
        FileSystem fileSystem = os.getFileSystem();
        List<OSFileStore> fsArray = fileSystem.getFileStores();
        String osName = System.getProperty("os.name");
        long available = 0;
        long total = 0;
        for (OSFileStore fs : fsArray){
            // windows 需要将所有磁盘分区累加，linux 和 mac 直接累加会出现磁盘重复的问题，待修复
            if(osName.toLowerCase().startsWith(Constants.WIN)) {
                available += fs.getUsableSpace();
                total += fs.getTotalSpace();
            } else {
                available = fs.getUsableSpace();
                total = fs.getTotalSpace();
                break;
            }
        }
        long used = total - available;
        diskInfo.put(TOTAL, total > 0 ? FileUtils.getSize(total) : "?");
        diskInfo.put(AVAILABLE, FileUtils.getSize(available));
        diskInfo.put(USED, FileUtils.getSize(used));
        if(total != 0){
            diskInfo.put(USAGE_RATE, df.format(used/(double)total * 100));
        } else {
            diskInfo.put(USAGE_RATE, 0);
        }
        return diskInfo;
    }

    /**
     * 获取交换区信息
     * @param memory /
     * @return /
     */
    private Map<String,Object> getSwapInfo(GlobalMemory memory) {
        Map<String,Object> swapInfo = new LinkedHashMap<>();
        VirtualMemory virtualMemory = memory.getVirtualMemory();
        long total = virtualMemory.getSwapTotal();
        long used = virtualMemory.getSwapUsed();
        swapInfo.put(TOTAL, FormatUtil.formatBytes(total));
        swapInfo.put(USED, FormatUtil.formatBytes(used));
        swapInfo.put(AVAILABLE, FormatUtil.formatBytes(total - used));
        if(used == 0){
            swapInfo.put(USAGE_RATE, 0);
        } else {
            swapInfo.put(USAGE_RATE, df.format(used/(double)total * 100));
        }
        return swapInfo;
    }

    /**
     * 获取内存信息
     * @param memory /
     * @return /
     */
    private Map<String,Object> getMemoryInfo(GlobalMemory memory) {
        Map<String,Object> memoryInfo = new LinkedHashMap<>();
        memoryInfo.put(TOTAL, FormatUtil.formatBytes(memory.getTotal()));
        memoryInfo.put(AVAILABLE, FormatUtil.formatBytes(memory.getAvailable()));
        memoryInfo.put(USED, FormatUtil.formatBytes(memory.getTotal() - memory.getAvailable()));
        memoryInfo.put(USAGE_RATE, df.format((memory.getTotal() - memory.getAvailable())/(double)memory.getTotal() * 100));
        return memoryInfo;
    }

    /**
     * 获取Cpu相关信息
     * @param processor /
     * @return /
     */
    private Map<String,Object> getCpuInfo(CentralProcessor processor) {
        Map<String,Object> cpuInfo = new LinkedHashMap<>();
        cpuInfo.put(NAME, processor.getProcessorIdentifier().getName());
        cpuInfo.put(PACKAGE, processor.getPhysicalPackageCount() + "个物理CPU");
        cpuInfo.put(CORE, processor.getPhysicalProcessorCount() + "个物理核心");
        cpuInfo.put(CORE_NUMBER, processor.getPhysicalProcessorCount());
        cpuInfo.put(LOGIC, processor.getLogicalProcessorCount() + "个逻辑CPU");
        // CPU信息
        long[] prevTicks = processor.getSystemCpuLoadTicks();
        // 默认等待300毫秒...
        long time = 300;
        Util.sleep(time);
        long[] ticks = processor.getSystemCpuLoadTicks();
        while (Arrays.toString(prevTicks).equals(Arrays.toString(ticks)) && time < 1000){
            time += 25;
            Util.sleep(25);
            ticks = processor.getSystemCpuLoadTicks();
        }
        long user = ticks[CentralProcessor.TickType.USER.getIndex()] - prevTicks[CentralProcessor.TickType.USER.getIndex()];
        long nice = ticks[CentralProcessor.TickType.NICE.getIndex()] - prevTicks[CentralProcessor.TickType.NICE.getIndex()];
        long sys = ticks[CentralProcessor.TickType.SYSTEM.getIndex()] - prevTicks[CentralProcessor.TickType.SYSTEM.getIndex()];
        long idle = ticks[CentralProcessor.TickType.IDLE.getIndex()] - prevTicks[CentralProcessor.TickType.IDLE.getIndex()];
        long iowait = ticks[CentralProcessor.TickType.IOWAIT.getIndex()] - prevTicks[CentralProcessor.TickType.IOWAIT.getIndex()];
        long irq = ticks[CentralProcessor.TickType.IRQ.getIndex()] - prevTicks[CentralProcessor.TickType.IRQ.getIndex()];
        long softirq = ticks[CentralProcessor.TickType.SOFTIRQ.getIndex()] - prevTicks[CentralProcessor.TickType.SOFTIRQ.getIndex()];
        long steal = ticks[CentralProcessor.TickType.STEAL.getIndex()] - prevTicks[CentralProcessor.TickType.STEAL.getIndex()];
        long totalCpu = user + nice + sys + idle + iowait + irq + softirq + steal;
        cpuInfo.put(USED, df.format(100d * user / totalCpu + 100d * sys / totalCpu));
        cpuInfo.put(IDLE, df.format(100d * idle / totalCpu));
        return cpuInfo;
    }

    /**
     * 获取系统相关信息,系统、运行天数、系统IP
     * @param os /
     * @return /
     */
    private Map<String,Object> getSystemInfo(OperatingSystem os){
        Map<String,Object> systemInfo = new LinkedHashMap<>();
        // jvm 运行时间
        long time = ManagementFactory.getRuntimeMXBean().getStartTime();
        Date date = new Date(time);
        // 计算项目运行时间
        String formatBetween = DateUtil.formatBetween(date, new Date(), Level.HOUR);
        // 系统信息
        systemInfo.put(OS, os.toString());
        systemInfo.put(DAY, formatBetween);
        systemInfo.put(IP, StringUtils.getLocalIp());
        return systemInfo;
    }
}
