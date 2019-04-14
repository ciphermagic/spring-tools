package com.cipher.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 文件处理工具类
 *
 * @author cipher
 */
public class FileOperateUtil {

    private static final Logger LOG = LoggerFactory.getLogger(FileOperateUtil.class);

    /**
     * 缓存区大小
     */
    public static final int BUFSIZE = 1024 * 1024;

    /**
     * 默认字符编码
     */
    public static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * 下载文件到本地
     *
     * @param fileUrl
     */
    public static File downloadFile(String fileUrl) {
        String fileLocal = System.getProperty("java.io.tmpdir") + File.separator + FileOperateUtil.rename(fileUrl);
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection urlCon = (HttpURLConnection) url.openConnection();
            urlCon.setConnectTimeout(6000);
            urlCon.setReadTimeout(6000);
            urlCon.setRequestProperty("Referer", "https://admin.tupperware.net.cn");
            int code = urlCon.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("文件读取失败");
            }
            //读文件流
            DataInputStream in = new DataInputStream(urlCon.getInputStream());
            DataOutputStream out = new DataOutputStream(new FileOutputStream(fileLocal));
            byte[] buffer = new byte[2048];
            int count = 0;
            while ((count = in.read(buffer)) > 0) {
                out.write(buffer, 0, count);
            }
            out.close();
            in.close();
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        File file = new File(fileLocal);
        if (!file.exists()) {
            throw new RuntimeException("文件下载失败");
        }
        return file;
    }

    /**
     * 功能描述: <br>
     * 批量删除文件
     *
     * @param files
     */
    public static boolean deleteFiles(String[] files) {
        boolean isSuccess = false;
        for (String f : files) {
            File file = new File(f);
            if (file.exists()) {
                isSuccess = file.delete();
            }
        }
        return isSuccess;
    }

    /**
     * 功能描述: <br>
     * 批量删除文件
     *
     * @param files
     */
    public static boolean deleteFiles(List<File> files) {
        boolean isSuccess = false;
        for (File f : files) {
            if (f.exists()) {
                isSuccess = f.getAbsoluteFile().delete();
            }
        }
        return isSuccess;
    }

    /**
     * 功能描述: <br>
     * 删除单个文件
     *
     * @param file
     */
    public static boolean deleteFile(File file) {
        boolean isSuccess = false;
        if (file != null && file.exists()) {
            file.delete();
        }
        return isSuccess;
    }

    /**
     * 功能描述: <br>
     * 根据当前时间生成随机文件名称
     *
     * @param name
     * @return
     */
    public static String rename(final String name) {
        final Long now = Long.parseLong(new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));
        final Long random = (long) (Math.random() * now);
        String fileName = now + random + "";
        if (name.indexOf(".") != -1) {
            fileName += name.substring(name.lastIndexOf("."));
        }
        return fileName;
    }

    /**
     * 功能描述: <br>
     * 获取文件名称后缀
     *
     * @param name
     * @return
     */
    public static String suffix(final String name) {
        String suffix = "";
        if (name.lastIndexOf(".") != -1) {
            suffix = name.substring(name.lastIndexOf(".") + 1);
        }
        return suffix;
    }

    /**
     * 功能描述: <br>
     * 截取文件名称中"."之前的字符串
     *
     * @param name
     * @return
     */
    public static String getBaseName(String name) {
        int index = name.lastIndexOf(".");
        if (index != -1) {
            name = name.substring(0, index);
        }
        return name;
    }

    /**
     * 功能描述: <br>
     * 生成压缩名称（xx.zip）
     *
     * @param name
     * @return
     */
    public static String zipName(final String name) {
        String prefix = "";
        if (-1 == name.lastIndexOf(".")) {
            prefix = name;
        } else {
            prefix = name.substring(0, name.lastIndexOf("."));
        }
        return prefix + ".zip";
    }

    /**
     * 功能描述: <br>
     * 把多个文件合并成一个并输出到指定目录中
     *
     * @param outFile
     * @param files
     */
    public static void mergeFiles(String outFile, String[] files) {
        FileChannel outChannel = null;
        FileInputStream fis = null;
        FileChannel fc = null;
        try {
            outChannel = new FileOutputStream(outFile).getChannel();
            for (String f : files) {
                fis = new FileInputStream(f);
                fc = fis.getChannel();
                ByteBuffer bb = ByteBuffer.allocate(1024 * 8);
                while (fc.read(bb) != -1) {
                    bb.flip();
                    outChannel.write(bb);
                    bb.clear();
                }
            }
        } catch (IOException e) {
            LOG.error("", e);
            throw new RuntimeException(e.getMessage());
        } finally {
            closeResource(outChannel);
            closeResource(fis);
            closeResource(fc);
        }
    }

    /**
     * 功能描述: <br>
     * 关闭资源
     *
     * @param res
     */
    public static void closeResource(Closeable res) {
        if (res != null) {
            try {
                res.close();
            } catch (IOException e) {
                LOG.error("", e);
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    /**
     * 功能描述: <br>
     * 创建目录
     *
     * @param dir
     */
    public static boolean makeDir(String dir) {
        boolean isSuccess = true;
        final File file = new File(dir);
        if (!file.exists() && !file.isDirectory()) {
            isSuccess = file.mkdirs();
        }
        return isSuccess;
    }



    /**
     * 功能描述: <br>
     * 从MultipartFile中获取文件字节数组
     *
     * @param file
     * @return
     */
    public static byte[] getBytes(MultipartFile file) {
        byte[] bytes = null;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            LOG.error("" + e);
            throw new RuntimeException(e.getMessage());
        }
        return bytes;
    }

    /**
     * 功能描述: <br>
     * 从MultipartFile中复制文件到指定目录
     *
     * @param fileFullName
     * @param mFile
     * @return
     */
    public static int copy(String fileFullName, MultipartFile mFile) {
        int byteCount = 0;
        OutputStream out = null;
        InputStream in = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(fileFullName));
            in = mFile.getInputStream();
            byteCount = FileCopyUtils.copy(in, out);
        } catch (IOException e) {
            LOG.error("" + e);
            throw new RuntimeException(e.getMessage());
        } finally {
            closeResource(in);
            closeResource(out);
        }
        return byteCount;
    }

    /**
     * 功能描述: <br>
     * 从MultipartFile中复制文件到指定目录
     *
     * @param file
     * @param mFile
     * @return
     */
    public static int copy(File file, MultipartFile mFile) {
        int byteCount = 0;
        OutputStream out = null;
        InputStream in = null;
        try {
            out = new BufferedOutputStream(new FileOutputStream(file));
            in = mFile.getInputStream();
            byteCount = FileCopyUtils.copy(in, out);
        } catch (IOException e) {
            LOG.error("" + e);
            throw new RuntimeException(e.getMessage());
        } finally {
            closeResource(in);
            closeResource(out);
        }
        return byteCount;
    }

    /**
     * 功能描述: <br>
     * 从response中获取输出流
     *
     * @param response
     * @return
     */
    public static OutputStream getOutputStream(HttpServletResponse response) {
        OutputStream out = null;
        try {
            out = response.getOutputStream();
        } catch (IOException e) {
            LOG.error("" + e);
            throw new RuntimeException(e.getMessage());
        }
        return out;
    }

    /**
     * outputStream flush
     *
     * @param os
     */
    public static void flush(OutputStream os) {
        try {
            os.flush();
        } catch (IOException e) {
            LOG.error("" + e);
            throw new RuntimeException(e.getMessage());
        } finally {
            FileOperateUtil.closeResource(os);
        }
    }

    /**
     * 功能描述: <br>
     * 将字符串写入指定路径的文件
     *
     * @param str
     * @param filePath
     */
    public static void writeFile(String str, String filePath) {
        byte[] buff = new byte[]{};
        FileOutputStream out = null;
        buff = str.getBytes();
        try {
            out = new FileOutputStream(filePath);
            out.write(buff, 0, buff.length);
        } catch (FileNotFoundException e) {
            LOG.error("" + e);
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            LOG.error("" + e);
            throw new RuntimeException(e.getMessage());
        } finally {
            closeResource(out);
        }
    }

    /**
     * 功能描述: <br>
     * 从指定路径的文件中读取字符串
     *
     * @param filePath
     * @return
     */
    public static String readFile(String filePath) {
        String str = null;
        CharBuffer cbuf = null;
        FileReader fReader = null;
        File file = new File(filePath);
        try {
            fReader = new FileReader(file);
            cbuf = CharBuffer.allocate((int) file.length());
            fReader.read(cbuf);
            str = new String(cbuf.array());
        } catch (FileNotFoundException e) {
            LOG.error("" + e);
            throw new RuntimeException(e.getMessage());
        } catch (IOException e) {
            LOG.error("" + e);
            throw new RuntimeException(e.getMessage());
        } finally {
            closeResource(fReader);
        }
        return str;
    }

    /**
     * 删除指定文件夹中指定时间间隔以前的文件或目录
     *
     * @param delpath
     */
    public static void deleteDir(String delpath, long interval) {
        File file = new File(delpath);
        if (file.isDirectory()) {
            String[] filelist = file.list();
            for (int i = 0; i < filelist.length; i++) {
                File delfile = new File(delpath + File.separator + filelist[i]);
                long lastModified = delfile.lastModified();
                long now = System.currentTimeMillis();
                if ((now - lastModified) > interval) {
                    if (!delfile.isDirectory()) {
                        delfile.delete();
                    } else if (delfile.isDirectory()) {
                        deleteDir(delpath + File.separator + filelist[i], interval);
                    }
                }
            }
        }
    }

}
