import java.io.*;

public class TestFileIo {
    public static void main(String[] args) throws Exception {
        System.out.println("Testing File I/O instrumentation...");
        
        // Test file write
        String testFile = "test-io.txt";
        try (FileOutputStream fos = new FileOutputStream(testFile)) {
            String content = "Hello, this is a test for File I/O monitoring!";
            fos.write(content.getBytes());
            System.out.println("Written " + content.length() + " bytes to " + testFile);
        }
        
        // Test file read
        try (FileInputStream fis = new FileInputStream(testFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead = fis.read(buffer);
            System.out.println("Read " + bytesRead + " bytes from " + testFile);
            System.out.println("Content: " + new String(buffer, 0, bytesRead));
        }
        
        // Test RandomAccessFile
        try (RandomAccessFile raf = new RandomAccessFile(testFile, "rw")) {
            raf.seek(0);
            String newContent = "Updated: ";
            raf.write(newContent.getBytes());
            System.out.println("Updated file with RandomAccessFile");
            
            raf.seek(0);
            byte[] buffer = new byte[100];
            int bytesRead = raf.read(buffer);
            System.out.println("Read " + bytesRead + " bytes with RandomAccessFile");
            System.out.println("Content: " + new String(buffer, 0, bytesRead));
        }
        
        // Clean up
        new File(testFile).delete();
        System.out.println("Test file deleted");
        
        System.out.println("\nFile I/O test completed!");
        
        // Keep the program running for a bit to allow metrics collection
        Thread.sleep(2000);
    }
}
