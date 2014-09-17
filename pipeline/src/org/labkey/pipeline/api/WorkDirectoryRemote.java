/*
 * Copyright (c) 2008-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.pipeline.api;

import org.labkey.api.pipeline.WorkDirFactory;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.InitializingBean;

import java.io.*;
import java.nio.channels.FileLock;
import java.nio.channels.FileChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Map;
import java.util.HashMap;

/**
 * Used to copy files from (and back to) a remote file system so that they can be used directly on the local file system 
 *
 * @author jeckels
 */
public class WorkDirectoryRemote extends AbstractWorkDirectory
{
    private static Logger _systemLog = Logger.getLogger(WorkDirectoryRemote.class);

    private static final int FILE_LOCKS_DEFAULT = 5;
    
    private final File _lockDirectory;

    private static final Map<File, Lock> _locks = new HashMap<>();

    @Override
    public File inputFile(File fileInput, boolean forceCopy) throws IOException
    {
        return copyInputFile(fileInput);
    }

    @Override
    public File inputFile(File fileInput, File fileWork, boolean forceCopy) throws IOException
    {
        return copyInputFile(fileInput, fileWork);
    }

    public static class Factory extends AbstractFactory implements InitializingBean
    {
        private String _lockDirectory;
        private String _tempDirectory;
        private boolean _sharedTempDirectory;
        private boolean _cleanupOnStartup;

        public void afterPropertiesSet() throws Exception
        {
            if (_tempDirectory == null)
            {
                throw new IllegalStateException("tempDirectory not set - set it directly using the tempDirectory property or use the tempDirectoryEnv property to point to an environment variable");
            }
            if (_cleanupOnStartup)
            {
                FileUtil.deleteDirectoryContents(new File(_tempDirectory));
            }
        }

        public WorkDirectory createWorkDirectory(String jobId, FileAnalysisJobSupport support, Logger log) throws IOException
        {
            File tempDir;
            int attempt = 0;
            do
            {
                // We've seen very intermittent problems failing to create temp files in the past during the DRTs,
                // so try a few times before failing
                File dirParent = (_tempDirectory == null ? null : new File(_tempDirectory));

                // If the temp directory is shared, then create a jobId directory to be sure the
                // work directory path is unique.
                try
                {
                    if (_sharedTempDirectory)
                    {
                        dirParent = File.createTempFile(jobId, "", dirParent);
                        dirParent.delete();
                        dirParent.mkdirs();
                    }

                    String name = support.getBaseName();
                    if (name.length() > 10)
                    {
                        // Don't let the total path get too long - Windows doesn't like paths longer than 255 characters
                        // so if there's a ridulously long file name, we don't want to duplicate its name in the
                        // directory too
                        name = name.substring(0, 9);
                    }
                    tempDir = File.createTempFile(name, WORK_DIR_SUFFIX, dirParent);
                    tempDir.delete();
                    tempDir.mkdirs();
                }
                catch (IOException e)
                {
                    IOException ioException = new IOException("Failed to create local working directory in the tempDirectory "
                            + dirParent + ", specified in the tempDirectory property in the pipeline configuration");
                    ioException.initCause(e);
                    _systemLog.error(ioException.getMessage(), e);
                    throw ioException;
                }
                attempt++;
            }
            while (attempt < 5 && !tempDir.isDirectory());
            if (!tempDir.isDirectory())
            {
                throw new IOException("Failed to create local working directory " + tempDir);
            }

            File lockDir = (_lockDirectory == null ? null : new File(_lockDirectory));
            return new WorkDirectoryRemote(support, this, log, lockDir, tempDir);
        }

        public String getLockDirectory()
        {
            if (_lockDirectory!= null)
            {
                // Do the validation on get instead of set because we may not have the NetworkDrive
                // configuration loaded in time at startup
                File lockDir = new File(_lockDirectory);
                if (!NetworkDrive.exists(lockDir) || !lockDir.isDirectory())
                    throw new IllegalArgumentException("The lock directory " + _lockDirectory + " does not exist.");
            }
            return _lockDirectory;
        }

        public void setLockDirectory(String directoryString)
        {
            _lockDirectory = directoryString;
        }

        public String getTempDirectory()
        {
            if (_tempDirectory != null)
            {
                // Do the validation on get instead of set because we may not have the NetworkDrive
                // configuration loaded in time at startup
                File tempDir = new File(_tempDirectory);
                if (!NetworkDrive.exists(tempDir) || !tempDir.isDirectory())
                    throw new IllegalArgumentException("The temporary directory " + _tempDirectory + " does not exist.");
            }
            return _tempDirectory;
        }

        /** @param directoryString path of the directory to be used as scratch space */
        public void setTempDirectory(String directoryString)
        {
            _tempDirectory = directoryString;
        }

        public void setCleanupOnStartup(boolean cleanupOnStartup)
        {
            _cleanupOnStartup = cleanupOnStartup;
        }

        /**
         * Set to an environment variable set to the path to use for the temporary directory.
         * (e.g. some cluster schedulers initialize TMPDIR to a job specific temporary directory
         * which will be removed, if the job is cancelled)
         *
         * @param tempDirectoryVar environment variable name 
         */
        public void setTempDirectoryEnv(String tempDirectoryVar)
        {
            String tempDirectory = System.getenv(tempDirectoryVar);
            if (tempDirectory == null || tempDirectory.length() == 0)
                throw new IllegalArgumentException("The environment variable " + tempDirectoryVar + " does not exist:\n" + System.getenv());                
            setTempDirectory(tempDirectory);
        }

        /**
         * @return true if the root temporary directory will be shared by multiple tasks
         */
        public boolean isSharedTempDirectory()
        {
            return _sharedTempDirectory;
        }

        /**
         * Set to true, if the root temporary directory will be shared by multiple tasks.
         * This is usually not necessary on a scheduled computational cluster, where each
         * task is given a separate working environment.
         *
         * @param sharedTempDirectory true if the root temporary directory will be shared by multiple tasks
         */
        public void setSharedTempDirectory(boolean sharedTempDirectory)
        {
            _sharedTempDirectory = sharedTempDirectory;
        }
    }

    public WorkDirectoryRemote(FileAnalysisJobSupport support, WorkDirFactory factory, Logger log, File lockDir, File tempDir) throws IOException
    {
        super(support, factory, tempDir, log);

        _lockDirectory = lockDir;
    }

    /**
     * @return a pair, where the first value is the total number of locks, and the second value is the lock index that
     * should be used next
     */
    private MasterLockInfo parseMasterLock(RandomAccessFile masterIn, File masterLockFile) throws IOException
    {
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();
        byte[] b = new byte[128];
        int i;
        while ((i = masterIn.read(b)) != -1)
        {
            bOut.write(b, 0, i);
        }
        String line = new String(bOut.toByteArray(), "UTF-8").trim();
        int totalLocks = FILE_LOCKS_DEFAULT;
        int currentIndex = 0;
        if (line.length() > 0)
        {
            String[] parts = line.split(" ");
            try
            {
                currentIndex = Integer.parseInt(parts[0]);
            }
            catch (NumberFormatException e)
            {
                throw new IOException("Could not parse the current lock index from the master lock file " + masterLockFile + ", the value was: " + parts[0]);
            }

            if (parts.length > 1)
            {
                try
                {
                    totalLocks = Integer.parseInt(parts[1]);
                }
                catch (NumberFormatException e)
                {
                    throw new IOException("Could not parse the total number of locks from the master lock file " + masterLockFile + ", the value was: " + parts[1]);
                }
            }

            if (totalLocks < 1)
                totalLocks = FILE_LOCKS_DEFAULT;
        }

        if (currentIndex >= totalLocks)
        {
            currentIndex = 0;
        }
        return new MasterLockInfo(totalLocks, currentIndex);
    }

    /**
     * File system locks are fine to communicate locking between two different processes, but they don't work for
     * multiple threads inside the same VM. We need to do Java-level locking as well.
     */
    private static synchronized Lock getInMemoryLockObject(File f)
    {
        Lock result = _locks.get(f);
        if (result == null)
        {
            result = new ReentrantLock();
            _locks.put(f, result);
        }
        return result;
    }

    protected CopyingResource createCopyingLock() throws IOException
    {
        if (_lockDirectory == null)
        {
            return new SimpleCopyingResource();
        }

        _jobLog.debug("Starting to acquire lock for copying files");

        MasterLockInfo lockInfo;

        // Synchronize to prevent multiple threads from trying to lock the master file from within the same VM
        synchronized (WorkDirectoryRemote.class)
        {
            RandomAccessFile randomAccessFile = null;
            FileLock masterLock = null;

            try
            {
                File masterLockFile = new File(_lockDirectory, "counter");
                randomAccessFile = new RandomAccessFile(masterLockFile, "rw");
                FileChannel masterChannel = randomAccessFile.getChannel();
                masterLock = masterChannel.lock();

                lockInfo = parseMasterLock(randomAccessFile, masterLockFile);
                int nextIndex = (lockInfo.getCurrentLock() + 1) % lockInfo.getTotalLocks();
                rewriteMasterLock(randomAccessFile, new MasterLockInfo(lockInfo.getTotalLocks(), nextIndex));
            }
            finally
            {
                if (randomAccessFile != null) { try { randomAccessFile.close(); } catch (IOException e) {} }
                if (masterLock != null) { try { masterLock.release(); } catch (IOException e) {} }
            }
        }

        _jobLog.debug("Acquiring lock #" + lockInfo.getCurrentLock());
        File f = new File(_lockDirectory, "lock" + lockInfo.getCurrentLock());
        FileChannel lockChannel = new FileOutputStream(f, true).getChannel();
        FileLockCopyingResource result = new FileLockCopyingResource(lockChannel, lockInfo.getCurrentLock(), f);
        _jobLog.debug("Lock #" + lockInfo.getCurrentLock() + " acquired");

        return result;
    }

    private void rewriteMasterLock(RandomAccessFile masterFile, MasterLockInfo lockInfo)
        throws IOException
    {
        masterFile.seek(0);

        String output = Integer.toString(lockInfo.getCurrentLock());
        if (lockInfo.getTotalLocks() != FILE_LOCKS_DEFAULT)
            output += " " + Integer.toString(lockInfo.getTotalLocks());
        byte[] outputBytes = output.getBytes("UTF-8");
        masterFile.write(outputBytes);
        masterFile.setLength(outputBytes.length);
    }

    private static class MasterLockInfo
    {
        private int _totalLocks;
        private int _currentLock;

        private MasterLockInfo(int totalLocks, int currentLock)
        {
            assert totalLocks > 0 : "Total locks must be greater than 0.";
            
            _totalLocks = totalLocks;
            _currentLock = currentLock;
        }

        public int getTotalLocks()
        {
            return _totalLocks;
        }

        public int getCurrentLock()
        {
            return _currentLock;
        }
    }

    public class FileLockCopyingResource extends SimpleCopyingResource
    {
        private FileChannel _channel;
        private final int _lockNumber;
        private FileLock _lock;
        private Throwable _creationStack;
        private Lock _memoryLock;

        public FileLockCopyingResource(FileChannel channel, int lockNumber, File f) throws IOException
        {
            _channel = channel;
            _lockNumber = lockNumber;
            _creationStack = new Throwable();

            // Lock the memory part first to eliminate multi-threaded access to the same file
            _memoryLock = getInMemoryLockObject(f);
            _memoryLock.lock();

            // Lock the file part second
            _lock = _channel.lock();
        }

        protected void finalize() throws Throwable
        {
            super.finalize();
            if (_lock != null)
            {
                _systemLog.error("FileLockCopyingResource was not released before it was garbage collected. Creation stack is: ", _creationStack);
            }
            close();
        }

        @Override
        public void close()
        {
            if (_lock != null)
            {
                // Unlock the file part first
                try { _lock.release(); } catch (IOException e) {}
                try { _channel.close(); } catch (IOException e) {}
                _jobLog.debug("Lock #" + _lockNumber + " released");
                _lock = null;
                _channel = null;
                super.close();

                // Unlock the memory part last
                _memoryLock.unlock();
                _memoryLock = null;
            }
        }
    }
}