package org.labkey.experiment;

import org.labkey.api.exp.ExperimentDataHandler;
import org.labkey.api.exp.ExperimentException;
import org.labkey.experiment.api.ExperimentRun;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.util.NetworkDrive;

import java.io.File;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileInputStream;
import java.util.*;

/**
 * User: jeckels
 * Date: Jul 28, 2006
 */
public abstract class URLRewriter
{
    protected Map<File, FileInfo> _files = new HashMap<File, FileInfo>();

    public abstract String rewriteURL(File f, ExpData data, ExperimentRun experimentRun) throws ExperimentException;

    public Collection<FileInfo> getFileInfos()
    {
        return _files.values();
    }

    protected static class FileInfo
    {
        private final File _file;
        private final String _name;
        private final ExperimentDataHandler _handler;
        private ExpData _data;

        public FileInfo(ExpData data, File file, String name, ExperimentDataHandler handler)
        {
            _file = file;
            _name = name;
            _handler = handler;
            _data = data;
        }

        public String getName()
        {
            return _name;
        }

        public void writeFile(final OutputStream out) throws ExperimentException, IOException
        {
            if (_handler != null)
            {
                _handler.exportFile(_data, _file, new OutputStream()
                {
                    private boolean _closed = false;

                    private void checkClosed()
                    {
                        if (_closed)
                        {
                            throw new IllegalStateException("Attempting to write to an OutputStream after it has been closed");
                        }
                    }
                    
                    public void write(byte b[]) throws IOException
                    {
                        checkClosed();
                        out.write(b);
                    }

                    public void write(byte b[], int off, int len) throws IOException
                    {
                        checkClosed();
                        out.write(b, off, len);
                    }

                    public void write(int b) throws IOException
                    {
                        checkClosed();
                        out.write(b);
                    }

                    public void close()
                    {
                        _closed = true;
                    }
                });
            }
            else
            {
                FileInputStream fIn = null;
                try
                {
                    fIn = new FileInputStream(_file);
                    byte[] b = new byte[4096];
                    int i;
                    while ((i = fIn.read(b)) != -1)
                    {
                        out.write(b, 0, i);
                    }
                }
                finally
                {
                    if (fIn != null) { try { fIn.close(); } catch (IOException e) {} }
                }
            }
        }

        public boolean hasContentToExport()
        {
            if (_handler != null)
            {
                return _handler.hasContentToExport(_data, _file);
            }
            else
            {
                return NetworkDrive.exists(_file) && _file.isFile();
            }
        }
    }
}
