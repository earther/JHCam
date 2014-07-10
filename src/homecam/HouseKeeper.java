package homecam;

import java.io.File;
import java.io.FileFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HouseKeeper extends Thread {
	public static final Logger log = LoggerFactory.getLogger(HouseKeeper.class);
	File dir;
	String[] filePrefix;
	String[] fileSuffix;
	int subDirectoryLevel = 0;
	boolean purgeSubDirectory = false;
	int timeoutHour = 72;

	public HouseKeeper(String filePrefix) {
		this(new String[] { filePrefix }, null);
	}

	public HouseKeeper(String[] filePrefix) {
		this(filePrefix, null);
	}

	public HouseKeeper(String[] filePrefix, String[] fileSuffix) {
		super("HouseKeeper");
		this.setFilePrefix(filePrefix);
		this.setFileSuffix(fileSuffix);
		this.setPriority(Thread.MIN_PRIORITY);
		this.setDaemon(true);
	}

	public void run() {
		run(dir, 0);
	}

	public String toString() {
		return "HouseKeeper " + timeoutHour + '@' + dir + ':' + filePrefix
				+ '-' + fileSuffix;
	}

	public void run(File dir, int level) {
		if (dir == null) {
			dir = new File(System.getProperty("java.io.tmpdir"));
		}
		if (!dir.canWrite()) {
			return;
		}

		final long acceptDate = System.currentTimeMillis()
				- (timeoutHour * 60 * 60 * 1000);
		log.info("{} @ {}", new java.util.Date(acceptDate), this);
		FileFilter houseKeepFilter = new FileFilter() {
			public boolean accept(File file) {
				boolean acceptPrefix = false;
				if (filePrefix != null && filePrefix.length > 0) {
					for (int i = 0; i < filePrefix.length; ++i) {
						String n = filePrefix[i];
						if (file.getName().startsWith(n)) {
							acceptPrefix = true;
						}
					}
				} else {
					acceptPrefix = true;
				}

				boolean acceptSuffix = false;
				if (fileSuffix != null && fileSuffix.length > 0) {
					for (int i = 0; i < fileSuffix.length; ++i) {
						String n = fileSuffix[i];
						if (file.getName().endsWith(n)) {
							acceptSuffix = true;
						}
					}
				} else {
					acceptSuffix = true;
				}

				if (!acceptPrefix || !acceptSuffix) {
					return false;
				}

				if (file.lastModified() >= acceptDate) {
					return false;
				}

				if (!file.isFile()) {
					return false;
				}

				boolean ret = file.delete();
				log.info("delete {} {}", file, ret);
				return false;
			}
		};
		dir.listFiles(houseKeepFilter);

		if (level < subDirectoryLevel) {
			++level;
			FileFilter subDirFilter = new FileFilter() {
				public boolean accept(File file) {
					return file.isDirectory();
				}
			};
			File[] subdirs = dir.listFiles(subDirFilter);
			if (subdirs != null) {
				for (File subdir : subdirs) {
					run(subdir, level);
					if (purgeSubDirectory) {
						File[] content = subdir.listFiles();
						if (content == null || content.length == 0) {
							boolean ret = subdir.delete();
							log.info("delete {} {}", subdir, ret);
						}
					}
				}
			}
		}
	}

	public File getDir() {
		return dir;
	}

	public HouseKeeper setDirName(String dir) {
		this.dir = new File(dir);
		return this;
	}

	public HouseKeeper setDir(File dir) {
		this.dir = dir;
		return this;
	}

	public String[] getFilePrefix() {
		return filePrefix;
	}

	public HouseKeeper setFilePrefix(String[] filePrefix) {
		this.filePrefix = filePrefix;
		return this;
	}

	public int getTimeoutHour() {
		return timeoutHour;
	}

	public HouseKeeper setTimeoutHour(int timeoutHour) {
		this.timeoutHour = timeoutHour;
		return this;
	}

	public HouseKeeper setTimeoutDay(int timeoutDay) {
		this.timeoutHour = timeoutDay * 24;
		return this;
	}

	public String[] getFileSuffix() {
		return fileSuffix;
	}

	public HouseKeeper setFileSuffix(String[] fileSuffix) {
		this.fileSuffix = fileSuffix;
		return this;
	}

	public int getSubDirectoryLevel() {
		return subDirectoryLevel;
	}

	public void setSubDirectoryLevel(int subDirectoryLevel) {
		this.subDirectoryLevel = subDirectoryLevel;
	}

	public boolean isPurgeSubDirectory() {
		return purgeSubDirectory;
	}

	public void setPurgeSubDirectory(boolean purgeSubDirectory) {
		this.purgeSubDirectory = purgeSubDirectory;
	}
}
