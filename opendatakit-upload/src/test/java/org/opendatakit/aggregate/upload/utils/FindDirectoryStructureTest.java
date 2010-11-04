/*
 * Copyright (C) 2010 University of Washington.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.opendatakit.aggregate.upload.applet;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JApplet;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.opendatakit.aggregate.upload.ui.SubmissionUploaderPanel;
import org.opendatakit.aggregate.upload.utils.BaseURLFinder;

public class UploadApplet extends JApplet{	

	public static final String ODK_INSTANCES_DIR = "/odk/instances";
	public static final String AGGREGATE_SUBMISSION_SERVLET = "/submission";
	
	public static String LOGGER = "UploadApplet";
	private static final long serialVersionUID = 1067499473847917508L;
	
	private Logger _logger; 
	private URL _submissionURL; 
	
	@Override
	public void init() 
	{
		// Collect errors during setup
		ArrayList<String> errors = new ArrayList<String>();
		
        // Set up Logger
        Logger logger = Logger.getLogger(LOGGER);
        logger.setLevel(Level.ALL);
        _logger = logger;
		
        // Get submission URL
        try 
        {
			URL baseURL = BaseURLFinder.getBaseURL(this.getCodeBase());
			URL submissionURL = new URL(baseURL.toString() + AGGREGATE_SUBMISSION_SERVLET);
			_submissionURL = submissionURL;
		} 
        catch (MalformedURLException e) 
		{
        	errors.add("Bad URL: " + this.getCodeBase());
        	getLogger().severe("Bad URL: " + this.getCodeBase());
		}
        
        // Set up GUI
        try 
        {
            SwingUtilities.invokeAndWait(new Runnable() 
            {
                public void run() 
                {
                    createSubmissionUploaderPanel(_submissionURL);
                }
            });
        } 
        catch (Exception e) 
        { 
        	errors.add("Could not create applet interface.");
            getLogger().severe("Could not create GUI.");
        }
        
        // If we have errors, show them to the user
        if (!errors.isEmpty())
        	JOptionPane.showMessageDialog(this, errors.toArray());
	}
	
	private void createSubmissionUploaderPanel(URL submissionURL)
	{
		SubmissionUploaderPanel panel = new SubmissionUploaderPanel(ODK_INSTANCES_DIR);
		panel.setServerURL(submissionURL);
		panel.setOpaque(true);
		setContentPane(panel);
	}
	
	public Logger getLogger() {
		if (_logger == null)
		{
			Logger logger = Logger.getLogger(LOGGER);
			logger.setLevel(Level.OFF);
			_logger = logger;
		}
			return _logger;
	}

	public void setLogger(Logger logger) {
		_logger = logger;
	}
}
package org.opendatakit.aggregate.upload.submission;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.BasicResponseHandler;

/**
 * Submission represents a single submission destined for a specific ODK Aggregate server.
 * A single submission is a folder which contains a filled out xform and accompanying files.
 * 
 * @author dylan@cs.washington.edu
 *
 */
public class Submission implements Callable<SubmissionResult>
{
	public static final String EXTENSION_XML =".xml";
	public static final String EXTENSION_PNG = ".png";
	public static final String EXTENSION_JPG = ".jpg";
	public static final String EXTENSION_JPEG = "jpeg";
	
	public static final String MIME_XML = "text/xml";
	public static final String MIME_PNG = "image/png";
	public static final String MIME_JPEG = "image/jpeg";
	
	public static final String FORM_PART_XML_SUBMISSION_FILE = "xml_submission_file";
	
	private final HttpClient _httpClient;
	private final URL _aggregateURL;
	private final File _submissionDir;
	private Logger _logger;

	/**
	 * Construct a Submission using the given http client, aggregate url,
	 * submission dir, and logger.
	 * 
	 * @param httpClient an org.apache.http.client.HttpClient
	 * @param aggregateURL
	 * @param submissionDir
	 * @param logger
	 */
	Submission(HttpClient httpClient, URL aggregateURL, File submissionDir, Logger logger)
	{
		_httpClient = httpClient;
		_aggregateURL = aggregateURL;
		_submissionDir = submissionDir;
		_logger = logger;
	}

	@Override
	public SubmissionResult call() 
	{
        SubmissionResult result = new SubmissionResult(_submissionDir, _aggregateURL, false);

        // prepare response and return uploaded
        String response = null;
        try 
        {
    		HttpPost httppost = buildSubmissionPost(_aggregateURL, _submissionDir);
        	ResponseHandler<String> handler = new BasicResponseHandler();
            response = _httpClient.execute(httppost, handler);
        } 
        catch (Exception e)
        {
        	getLogger().severe(Arrays.toString(e.getStackTrace()));
        	getLogger().severe(e.toString());
        	result.setSuccess(false);
        	result.setFailureReason(e.toString());
        	return result;
        }
//        catch (HttpResponseException e)
//        {
//        	getLogger().severe(Arrays.toString(e.getStackTrace()));
//        	getLogger().severe(e.getMessage());
//        	result.setSuccess(false);
//        	return result;
//        }
//        catch (ClientProtocolException e) 
//        {
//        	getLogger().severe(Arrays.toString(e.getStackTrace()));
//        	getLogger().severe(e.getMessage());
//        	result.setSuccess(false);
//        	return result;
//        } 
//        catch (IOException e) 
//        {
//        	getLogger().severe(Arrays.toString(e.getStackTrace()));
//        	getLogger().severe(e.getMessage());
//        	result.setSuccess(false);
//        	return result;
//        } catch (URISyntaxException e) {
//        	getLogger().severe(Arrays.toString(e.getStackTrace()));
//        	getLogger().severe(e.getMessage());
//        	result.setSuccess(false);
//        	return result;
//		} catch (Exception e)
//		{
//        	getLogger().severe(Arrays.toString(e.getStackTrace()));
//        	getLogger().severe(e.getMessage());
//        	result.setSuccess(false);
//        	return result;
//		}

        getLogger().info("Response:" + response);
        result.setSuccess(response != null);
        return result;        
	}

	/**
	 * Returns an org.apache.http.client.methods.HttpPost built to post to the
	 * given url. Builds a post that ODK Aggregate will recognize as a
	 * submission.
	 * 
	 * @param url
	 *            the URL of the ODK Aggregate submission servlet
	 * @param submissionDir
	 *            the submission, i.e. a File representing a directory
	 *            containing submission files
	 * @return an HttpPost
	 * @throws URISyntaxException
	 *             if the given url could not be converted to a URI for the
	 *             HttpPost
	 * @throws IllegalArgumentException
	 *             if the submissionDir is invalid, i.e. it has more than one
	 *             XML file
	 */
	protected HttpPost buildSubmissionPost(URL url, File submissionDir) throws URISyntaxException
	{
		URI uri = url.toURI();
		HttpPost post = new HttpPost(uri);
		boolean seenXML = false;

		// mime post
        MultipartEntity entity = new MultipartEntity();
        for (File f : submissionDir.listFiles()) 
        {
            if (f.getName().endsWith(EXTENSION_XML)) 
            {
            	if (seenXML)
            	{
            		throw new IllegalArgumentException(String.format("submissionDir (%s) has more than one xml file!", submissionDir));
            	}
            	seenXML = true;
                entity.addPart(FORM_PART_XML_SUBMISSION_FILE, new FileBody(f, MIME_XML));
                getLogger().info("added xml file " + f.getName());
            } 
            else if (f.getName().endsWith(EXTENSION_PNG)) 
            {
                entity.addPart(f.getName(), new FileBody(f, MIME_PNG));
                getLogger().info("added image file " + f.getName());
            } 
            else if (f.getName().endsWith(EXTENSION_JPG) || f.getName().endsWith(EXTENSION_JPEG)) 
            {
                entity.addPart(f.getName(), new FileBody(f, MIME_JPEG));
                getLogger().info("added image file " + f.getName());
            } 
            else 
            {
                getLogger().warning("unsupported file type, not adding file: " + f.getName());
            }
        }
        if (!seenXML)
        {
        	throw new IllegalArgumentException(String.format("submissionDir (%s) has no xml file!", submissionDir));
        }
        post.setEntity(entity);
        return post;
	}
	
	private Logger getLogger() {
		if (_logger == null)
		{
			Logger logger = Logger.getLogger(this.getClass().getName());
			logger.setLevel(Level.OFF);
			_logger = logger;
		}
			return _logger;
	}
}
package org.opendatakit.aggregate.upload.submission;

import java.io.File;
import java.net.URL;

/**
 * A SubmissionResult represents the result of an attempt to submit a Submission.
 * This is what will be returned as the result from Submission.call().
 * 
 * @author dylan
 *
 */
public class SubmissionResult {

	private static final String DEFAULT_FAILURE_REASON = "Unknown";
	
	private File _submissionFile;
	private boolean _success;
	private URL _aggregateURL;
	private String _reason;
	
	/**
	 * Construct a new SubmissionResult.
	 * 
	 * @param submissionFile the File (should be a directory) associated with this Submission.
	 * @param submissionURL the URL the Submission was sent to.
	 * @param success whether the Submission was successful or not.
	 */
	public SubmissionResult(File submissionFile, URL submissionURL, boolean success)
	{
		_submissionFile = submissionFile;
		_aggregateURL = submissionURL;
		_success = success;
		_reason = DEFAULT_FAILURE_REASON;
	}
	
	public URL getAggregateURL() {
		return _aggregateURL;
	}

	public void setAggregateURL(URL aggregateURL) {
		_aggregateURL = aggregateURL;
	}

	public File getFile()
	{
		return _submissionFile;
	}
	
	public boolean isSuccess()
	{
		return _success;
	}
	
	public void setFile(File submissionFile)
	{
		_submissionFile = submissionFile;
	}
	
	public void setSuccess(boolean success)
	{
		_success = success;
	}
	
	public void setFailureReason(String reason)
	{
		_reason = reason;
	}
	
	public String getFailureReason()
	{
		return _reason;
	}
}
package org.opendatakit.aggregate.upload.submission;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.client.HttpClient;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.opendatakit.aggregate.upload.utils.DeleteDirectory;

/**
 * Manages the process of submitting a group of submissions to an ODK Aggregate
 * server. Using a parent directory containing multiple submissions (e.g.
 * sdcard/odk/instances on an Android phone running ODK Collect), handles
 * uploading submissions, deleting successful submissions, and reporting
 * unsuccessful submissions back to the caller.
 * 
 * @author dylan
 * 
 */
public class SubmissionUploader 
{
	public static String LOGGER = "SubmissionUploader";
	
	private ExecutorService _executorService;
	private Logger _logger;
	private HttpClient _httpClient;
	
	/**
	 * Constructs the SubmissionUploader using the given http client and executor service.
	 * 
	 * @param httpClient the org.apache.http.client.HttpClient to use.
	 * @param executorService the ExecutorService to use.
	 */
	public SubmissionUploader(HttpClient httpClient, ExecutorService executorService)
	{
		_executorService = executorService;
		_httpClient = httpClient;
		
        // Set up Logger
        Logger logger = Logger.getLogger(LOGGER);
        logger.setLevel(Level.ALL);
        _logger = logger;
	}

	/**
	 * Uploads the given directory containing submission folders to the given
	 * aggregate instance and returns a list of results.
	 * 
	 * @param submissionsParentDir
	 *            a directory containing a set of subdirectories, each
	 *            containing a single submission
	 * @param submissionURL
	 *            the URI to post the submissions to (aggregate servlet)
	 * @return a List<Future<SubmissionResults>> representing the future result
	 *         of each submission
	 */
	public List<Future<SubmissionResult>> uploadSubmissions(File submissionsParentDir, URL submissionURL)
	{
        // loop through submissions
        File[] submissionFolders = submissionsParentDir.listFiles(new FileFilter() {
			
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});
        List<Future<SubmissionResult>> futures = new ArrayList<Future<SubmissionResult>>();
        for (File submissionFolder : submissionFolders)
        {
        	Submission submission = new Submission(_httpClient, submissionURL, submissionFolder, _logger);
        	Future<SubmissionResult> future = _executorService.submit(submission);
        	futures.add(future);        	
        }
        return futures;
	}

	/**
	 * Check that the submissions were uploaded successfully, and delete each
	 * successfully uploaded submission. Returns a list of submissions which
	 * were not uploaded successfully.
	 * 
	 * @param submissionResults
	 *            the List<Future<SubmissionResults>> obtained from calling
	 *            uploadSubmissions.
	 * @param logger
	 *            the logger to use for logging
	 * @return a List<SubmissionResult> containing the submissions which were
	 *         not successful
	 */
	public List<SubmissionResult> checkSubmissionResultsAndDeleteSubmissions(List<Future<SubmissionResult>> submissionResults)
	{
		List<SubmissionResult> failedSubmissions = new ArrayList<SubmissionResult>();
		for (Future<SubmissionResult> futureResult : submissionResults)
		{
			SubmissionResult result = null;
			try 
			{
				result = futureResult.get();
			} catch (InterruptedException e) 
			{
				getLogger().warning(Arrays.toString(e.getStackTrace()));
				getLogger().warning(e.getMessage());
			} catch (ExecutionException e) 
			{
				getLogger().warning(Arrays.toString(e.getStackTrace()));
				getLogger().warning(e.getMessage());
			}
			if (result != null && !result.isSuccess())
			{
				failedSubmissions.add(result);
				getLogger().warning("Submission failed: " + result.getFile() + ". Reason: " + result.getFailureReason());
			}
			else
			{
				File uploadedFile = result.getFile();
				try
				{
					boolean deleted = DeleteDirectory.deleteDirectory(uploadedFile);
					if (!deleted)
					{
						result.setFailureReason("Successful upload but unable to delete submission.");
						failedSubmissions.add(result);
						getLogger().warning("Successful upload but unable to delete file: " + uploadedFile);
					}
				}
				catch (SecurityException e)
				{
					result.setFailureReason(e.getMessage());
					failedSubmissions.add(result);
					getLogger().warning("Unable to delete file: " + uploadedFile);
				}
			}
		}
		return failedSubmissions;
	}
	
	/**
	 * Returns the logger associated with the SubmissionUploader. Guaranteed to
	 * return a non-null Logger.
	 * 
	 * @return the Logger associated with the SubmissionUploader.
	 */
	public Logger getLogger() {
		if (_logger == null)
		{
			Logger logger = Logger.getLogger(LOGGER);
			logger.setLevel(Level.OFF);
			_logger = logger;
		}
			return _logger;
	}

	public static void main(String[] args)
	{
		if (args.length == 2)
		{
			String submissionsParentDirString = args[0];
			String aggregateURLString = args[1];
			if (aggregateURLString.endsWith("/"))
				aggregateURLString = aggregateURLString.substring(0, aggregateURLString.length() - 1);
			
			File submissionsParentDir = new File(submissionsParentDirString);
			URL aggregateURL = null;
			
			try 
			{
				aggregateURL = new URL(aggregateURLString + "/submission");
			} 
			catch (MalformedURLException e) 
			{
				System.out.println("Bad URL:");
				System.out.println(e.getMessage());
			}
			System.out.println("Using submission dir: " + submissionsParentDir);
			System.out.println("Using aggregateURL: " + aggregateURL);
	        
			// configure connection
	        HttpParams params = new BasicHttpParams();
	        HttpConnectionParams.setConnectionTimeout(params, 30000);
	        HttpConnectionParams.setSoTimeout(params, 30000);
	        HttpClientParams.setRedirecting(params, false);

	        // setup client
	        SchemeRegistry registry = new SchemeRegistry();
	        registry.register(new Scheme(aggregateURL.getProtocol(), PlainSocketFactory.getSocketFactory(), aggregateURL.getPort()));
	        ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(params, registry);
	        HttpClient httpClient = new DefaultHttpClient(manager, params);
	
		    // Set up ExecutorService
		    ExecutorService es = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
			
			SubmissionUploader uploader = new SubmissionUploader(httpClient, es);
			uploader.uploadSubmissions(submissionsParentDir, aggregateURL);
		}
		else
		{
			System.out.println("Usage: SubmissionUploader <submissionsParentDir> <aggregateURL>");
			System.out.println("<submissionsParentDir>: a directory containing multiple directories, each one corresponding to a submission.");
			System.out.println("<aggregateURL>: the location of the aggregate instance to hit, e.g. 'http://localhost:8080/");
			System.out.println();
			System.out.println("Note: It is assumed that you have already uploaded the appropriate form definitions for the submissions.");
		}
	}
}
package org.opendatakit.aggregate.upload.ui;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.UIManager;

import org.apache.http.client.HttpClient;
import org.opendatakit.aggregate.upload.submission.SubmissionResult;
import org.opendatakit.aggregate.upload.submission.SubmissionUploader;
import org.opendatakit.aggregate.upload.utils.FindDirectoryStructure;
import org.opendatakit.aggregate.upload.utils.HttpClientFactory;

public class SubmissionUploaderPanel extends JPanel implements ActionListener{	
		
	private static final String COMMAND_CHANGE_SERVER = "Edit";
	private static final String COMMAND_REFRESH = "Refresh";
	private static final String COMMAND_SELECT = "Select";
	
	private static final long serialVersionUID = 6753077036860161654L;
	private static final String LOGGER = "SubmissionUploaderPanel";
	private static final String DEFAULT_DIRECTORY_STRUCTURE_TO_SEARCH_FOR = "/odk/instances";
	
	// So this runs first before JVM loads an L&F
	static
	{
		try 
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} 
		catch (Exception e) 
		{
			// Oh well, we will live with whatever L&F Java decides to use
		}
	}
	
	private Logger _logger;
	private String _directoryStructureToSearchFor;
	private List<String> _driveButtonActionCommands;
	
	// GUI components
	private JPanel _panel;
	private GridBagConstraints _c;
	
	private JLabel _serverLabel;
	private JTextField _serverLocationField;
	private JButton _serverChangeButton;
	
	private JLabel _driveLabel;
	private List<JRadioButton> _driveLocationButtons;
	private JButton _driveRefreshButton;
	
	private JLabel _manualDriveLabel;
	private JTextField _manualDriveLocationField;
	private JButton _manualDriveSelectButton;
	
	private enum Action
	{
		NONE,
		SEND_SUBMISSIONS,
		CHANGE_SERVER_FIELD,
		REFRESH_DRIVE_LOCATIONS;
	}
	
	public SubmissionUploaderPanel()
	{
		this(DEFAULT_DIRECTORY_STRUCTURE_TO_SEARCH_FOR);
	}
	
	public SubmissionUploaderPanel(String directoryStructureToSearchFor)
	{
        super(new BorderLayout());
		        
		Logger logger = Logger.getLogger(LOGGER);
		_logger = logger;
		
		_directoryStructureToSearchFor = FindDirectoryStructure.normalizePathString(directoryStructureToSearchFor);
		
		_panel = new JPanel();
		_panel.setLayout(new GridBagLayout());
		_c = new GridBagConstraints();
		// Set common constraint settings
		_c.anchor = GridBagConstraints.LINE_END;
		_c.fill = GridBagConstraints.BOTH;
		_c.insets = new Insets(15, 15, 15, 15);
		
		// Server settings components
		_serverLabel = new JLabel("Server submission location: ");
		_c.gridx = 0;
		_c.gridy = 0;
		_panel.add(_serverLabel, _c);
		
        _serverLocationField = new JTextField(20);
        _serverLocationField.addActionListener(this);
        _serverLocationField.setEditable(false);
        _c.gridx = 1;
        _c.gridy = 0;
        _panel.add(_serverLocationField);
        
        _serverChangeButton = new JButton("Change");
        _serverChangeButton.setActionCommand(COMMAND_CHANGE_SERVER);
        _serverChangeButton.addActionListener(this);
        _c.gridx = 2;
        _c.gridy = 0;
        _panel.add(_serverChangeButton, _c);
       
        // Set up components for finding the mounted drive
        setupDriveLocationComponents();
        
        _driveRefreshButton = new JButton("Refresh");
        _driveRefreshButton.setActionCommand(COMMAND_REFRESH);
        _driveRefreshButton.addActionListener(this);
        _c.gridx = 2;
        _c.gridy = 1;
        _panel.add(_driveRefreshButton, _c);

        _manualDriveLocationField = new JTextField(20);
        _manualDriveLocationField.addActionListener(this);
        _c.gridx = 1;
        _c.gridy = 2;
        _panel.add(_manualDriveLocationField, _c);
        
        _manualDriveSelectButton = new JButton("Select");
        _manualDriveSelectButton.setActionCommand(COMMAND_SELECT);
        _manualDriveSelectButton.addActionListener(this);
        _c.gridx = 2;
        _c.gridy = 2;
        _panel.add(_manualDriveSelectButton, _c);

        add(_panel, BorderLayout.LINE_START);
        setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
	}
	
	private boolean setupDriveLocationComponents()
	{
		// Clear any previous components
		if (_driveLabel != null)
		{
			_panel.remove(_driveLabel);
		}
		if (_driveLocationButtons != null)
		{
			for (JRadioButton driveLocationButton : _driveLocationButtons)
			{
				_panel.remove(driveLocationButton);
			}
		}
		if (_manualDriveLabel != null)
		{
			_panel.remove(_manualDriveLabel);
		}
		
		// Components for autodetection of drive
        List<File> submissionParentDirs = FindDirectoryStructure.searchMountedDrives(_directoryStructureToSearchFor);
        boolean foundDrives = submissionParentDirs.size() != 0;
        
        if (!foundDrives)
        {
        	_driveLabel = new JLabel("No possible drives were autodetected.");
        	_driveLabel.setOpaque(true);
        	_c.gridx = 0;
        	_c.gridy = 1;
        	_panel.add(_driveLabel, _c);
        }
        else
        {        	
        	_driveLabel = new JLabel("Select location of Android phone: ");
        	_driveLabel.setOpaque(true);
        	_c.gridx = 0;
        	_c.gridy = 1;
        	_panel.add(_driveLabel, _c);
        	
            ButtonGroup buttonGroup = new ButtonGroup();
        	_driveLocationButtons = new ArrayList<JRadioButton>();
        	_driveButtonActionCommands = new ArrayList<String>();
	        for (File submissionPDir : submissionParentDirs)
	        {
	        	String name = submissionPDir.getAbsolutePath(); // name = "/media/disk/odk/instances"
	        	int index = name.indexOf(_directoryStructureToSearchFor);
	        	if (index == -1)
	        	{
	        		String error = String.format("Could not find %s in %s. Please enter location manually.", _directoryStructureToSearchFor, name);
	        		getLogger().severe(error);
	        		JOptionPane.showMessageDialog(this, error , "Error", JOptionPane.ERROR_MESSAGE);
	            	JLabel errorLabel = new JLabel("Error autodetecting phone.");
	            	errorLabel.setOpaque(true);
	            	_c.gridx = 0;
	            	_c.gridy = 1;
	            	_panel.add(errorLabel, _c);
	        	}
	        	else
	        	{
	        		name = name.substring(0, index); // name = "/media/disk"
	        	}

	        	JRadioButton submissionPDirButton = new JRadioButton(name);
	        	submissionPDirButton.setMnemonic(name.charAt(0));
	        	submissionPDirButton.setActionCommand(name);
	        	_driveButtonActionCommands.add(name);
	        	submissionPDirButton.addActionListener(this);
	        	buttonGroup.add(submissionPDirButton);
	        	_driveLocationButtons.add(submissionPDirButton);
	        	_c.gridx = 1;
	        	_c.gridy = 1;
	        	_panel.add(submissionPDirButton, _c);
	        } 
	    }
        

        
        // Components for manual location
        if (!foundDrives)
        {
	    	_manualDriveLabel = new JLabel("Location of Android phone: ");
	    	_manualDriveLabel.setOpaque(true);
	    	_c.gridx = 0;
	    	_c.gridy = 2;
	    	_panel.add(_manualDriveLabel, _c);
        }
        else
        {
        	_manualDriveLabel = new JLabel("Or enter location manually: ");
        	_manualDriveLabel.setOpaque(true);
        	_c.gridx = 0;
        	_c.gridy = 2;
        	_panel.add(_manualDriveLabel, _c);
        }
        
        // Show changes
        _panel.validate();
        return foundDrives;
	}
	
	private void setWaitCursor(boolean wait)
	{
		if (wait)
		{
			_panel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
			Graphics2D g = (Graphics2D) getGraphics();
			g.setColor(Color.DARK_GRAY);
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
			g.fillRect(0, 0, getWidth(), getHeight());
			_panel.getRootPane().getGlassPane().paint(g);
			_panel.getRootPane().getGlassPane().setEnabled(true);
			_panel.getRootPane().getGlassPane().setVisible(true);
		}
		else
		{
			_panel.setCursor(Cursor.getDefaultCursor());
			_panel.getRootPane().getGlassPane().setVisible(false);
			_panel.getRootPane().getGlassPane().setEnabled(false);
		}
	}
	
	@Override
	public void actionPerformed(ActionEvent event) {
		String actionCommand = event.getActionCommand();
		Action action = Action.NONE;
		String driveLocationString = null;
		
		// Change server field to be editable
		if (actionCommand.equals(COMMAND_CHANGE_SERVER))
		{
			action = Action.CHANGE_SERVER_FIELD;
		}
		// Get radio button value
		else if (_driveButtonActionCommands != null && _driveButtonActionCommands.contains(actionCommand))
		{
			driveLocationString = event.getActionCommand();
			action = Action.SEND_SUBMISSIONS;
		}
		// Refresh drive locations
		else if (actionCommand.equals(COMMAND_REFRESH))
		{
			action = Action.REFRESH_DRIVE_LOCATIONS;
		}
		// Get text field
		else if (actionCommand.equals(COMMAND_SELECT))
		{
			driveLocationString = getDriveLocation();
			action = Action.SEND_SUBMISSIONS;
		}
		
		switch (action)
		{
		case NONE:
			break;
		case CHANGE_SERVER_FIELD:
			_serverChangeButton.setVisible(false);
			_serverLocationField.setEditable(true);
			break;
		case REFRESH_DRIVE_LOCATIONS:
			boolean foundDrives = setupDriveLocationComponents();
			if (!foundDrives)
			{
				JOptionPane.showMessageDialog(this, "Did not find any possible drives.");
			}
			break;
		case SEND_SUBMISSIONS:
			setWaitCursor(true);
			File submissionsParentDir = null;
			ArrayList<String> errors = new ArrayList<String>();

			// Add error message on invalid drive
			if (driveLocationString == null || driveLocationString.equals(""))
			{
				getLogger().warning("No location entered.");
				errors.add("No location entered.");
			}
			// Valid drive, proceed processing
			else
			{
				submissionsParentDir = new File(driveLocationString + _directoryStructureToSearchFor);
				if (!submissionsParentDir.exists())
				{
					getLogger().warning("Directory does not exist: " + submissionsParentDir);
					errors.add("Directory does not exist: " + submissionsParentDir);
				}
				else if(!submissionsParentDir.canRead())
				{
					getLogger().warning("Can't read " + submissionsParentDir);
					errors.add("Don't have permission to read " + submissionsParentDir);
				}
				else if (submissionsParentDir.list().length == 0)
				{
					getLogger().warning("Submissions parent directory is empty -- nothing to submit!");
					errors.add(submissionsParentDir + " is empty - nothing to submit!");
				}
			}
			
			URL serverURL = null;
			try 
			{
				serverURL = getServerURL();
			} 
			catch (MalformedURLException e) 
			{
				getLogger().warning("Bad URL for submission server location - " + e.getMessage());
				errors.add("Bad URL for server submission location.");
			}
			if (serverURL != null && submissionsParentDir != null && errors.isEmpty())
			{
				uploadSubmissions(serverURL, submissionsParentDir);
			}
			else if (!errors.isEmpty())
			{
				errors.add(0, "Please resolve the following problems:");
				JOptionPane.showMessageDialog(this, errors.toArray(), "Errors", JOptionPane.WARNING_MESSAGE);
			}
			setWaitCursor(false);
			break;
		}
	}

	/**
	 * Uploads the submissions, checks the results, and shows the results to the
	 * user.
	 * 
	 * @param submissionURL
	 *            the URL to post the submissions to
	 * @param submissionsParentDir
	 *            the directory containing multiple submissions (each submission
	 *            is a directory of its own)
	 */
	private void uploadSubmissions(URL submissionURL, File submissionsParentDir)
	{
		// Set up HttpClient
		HttpClient httpClient = HttpClientFactory.getHttpClient(submissionURL, getLogger());
        
        // Set up ExecutorService
        ExecutorService es = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        
        // Submit submissions
		SubmissionUploader uploader = new SubmissionUploader(httpClient, es); 
		List<Future<SubmissionResult>> submissionResults = uploader.uploadSubmissions(submissionsParentDir, submissionURL);
		
		// Check submission results
		List<SubmissionResult> failedSubmissions = uploader.checkSubmissionResultsAndDeleteSubmissions(submissionResults);
		String resultMessage = "";
		if (failedSubmissions != null && failedSubmissions.size() > 0)
		{
			String failedSubmissionsFiles = "";
			for (SubmissionResult result : failedSubmissions)
			{
				failedSubmissionsFiles += "    " + result.getFile().toString() + "    (" + result.getFailureReason() + ")\n";
			}
			try 
			{
				resultMessage = "Failed submissions for URL: " + getServerURL() + "\n" + failedSubmissionsFiles;
			}
			catch (MalformedURLException e)
			{
				resultMessage = "Bad URL. Please fix the URL and resubmit.";
			}
			getLogger().severe(resultMessage);
		}
		else
		{
			resultMessage = "All submissions successfully uploaded!";
			getLogger().info(resultMessage);
		}
		JOptionPane.showMessageDialog(this, resultMessage);
		
		// Shut down resources
		httpClient.getConnectionManager().shutdown();
		es.shutdown();
	}
	
	public String getDriveLocation()
	{
		return _manualDriveLocationField.getText();
	}
	
	public void setDriveLocation(String driveLocation)
	{
		_manualDriveLocationField.setText(driveLocation);
	}

	public URL getServerURL() throws MalformedURLException
	{
		return new URL(_serverLocationField.getText());
	}
	
	public void setServerURL(URL serverURL)
	{
		_serverLocationField.setText(serverURL.toString());
	}
	
	public Logger getLogger() {
		if (_logger == null)
		{
			Logger logger = Logger.getLogger(LOGGER);
			logger.setLevel(Level.OFF);
			_logger = logger;
		}
			return _logger;
	}

	public void setLogger(Logger logger) {
		_logger = logger;
	}
	
    /**
     * Create the GUI and show it.
     */
    private static void createAndShowGUI() {
		SubmissionUploaderPanel panel = new SubmissionUploaderPanel();
		
		JFrame frame = new JFrame("SubmissionUploaderPanel");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		panel.setOpaque(true);
		frame.setContentPane(panel);
		
		frame.pack();
		frame.setVisible(true);
    }

	
	public static void main(String[] args)
	{
		try
		{
			javax.swing.SwingUtilities.invokeLater(new Runnable()
			{
				public void run()
				{
					createAndShowGUI();
				}
			});
		}
		catch (Exception e)
		{
			System.out.println(e.getMessage());
		}
	}

}
package org.opendatakit.aggregate.upload.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BaseURLFinder {

	private static final Pattern URL_PATTERN = Pattern.compile("(^https?:\\/\\/[^\\/]+).*");
	
	/**
	 * Returns the base url of the given url. 
	 * http://www.google.com 				--> http://www.google.com
	 * http://www.google.com/something 		--> http://www.google.com
	 * https://www.google.com:80/?p=param	--> https://www.google.com:80
	 * 
	 * @param url the URL to find the base for
	 * @return the base URL
	 * @throws MalformedURLException 
	 */
	public static URL getBaseURL(URL url) throws MalformedURLException
	{
		String baseURLString = "";
		Pattern urlPattern = URL_PATTERN; 
		Matcher m = urlPattern.matcher(url.toString());
		if (m.matches() && m.groupCount() > 0)
		{
			baseURLString = m.group(1);
		}
		return new URL(baseURLString);
	}
}
package org.opendatakit.aggregate.upload.utils;

import java.io.File;

public class DeleteDirectory {

	/**
	 * Deletes the given file. Useful for recursively deleting non-empty
	 * directories.
	 * 
	 * @param path
	 *            the File to delete
	 * @return true if the File was successfully deleted, false otherwise.
	 */
	public static boolean deleteDirectory(File path) 
	{
		if(path.exists()) 
		{
	      File[] files = path.listFiles();
	      for(int i=0; i < files.length; i++) 
	      {
	         if(files[i].isDirectory()) 
	         {
	           deleteDirectory(files[i]);
	         }
	         else 
	         {
	           files[i].delete();
	         }
	      }
	    }
	    return(path.delete());
	}
}
package org.opendatakit.aggregate.upload.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FindDirectoryStructure 
{	
	private static final String PROPERTY_OS = "os.name";
	private static final String OS_WINDOWS = "Windows";
	private static final String OS_MAC = "Mac";
	
	private static final String[] MAC_MOUNTS = 
		new String[]{"/Volumes", "/media", "/mnt"};
	private static final String[] UNIX_MOUNTS = 
		new String[]{"/mnt", "/media"};

	/**
	 * Searches mounted drives for the given directory structure and returns a
	 * list of positive results. Works for Windows, Mac, and Linux operating
	 * systems.
	 * 
	 * @param directoryStructureToSearchFor
	 *            the directory structure to search for
	 * @return a List<File> containing matches of currently mounted file systems
	 *         which contain the directoryStructureToSearchFor
	 */
	public static List<File> searchMountedDrives(String directoryStructureToSearchFor)
	{
		directoryStructureToSearchFor = normalizePathString(directoryStructureToSearchFor);
		String os = System.getProperty(PROPERTY_OS);
		if (os.contains(OS_WINDOWS))
		{
			File[] drives = File.listRoots();
			List<String> driveStrings = new ArrayList<String>();
			if (drives != null)
			{
				for (File root : drives)
				{
					driveStrings.add(root.getAbsolutePath());
				}
				return search(driveStrings, directoryStructureToSearchFor);
			}
		}
		else if (os.contains(OS_MAC))
		{
			List<String> driveStrings = new ArrayList<String>();
			for (String mountPointString : MAC_MOUNTS)
			{
				File mountPoint = new File(mountPointString);
				if (mountPoint != null)
				{
					File[] mounts = mountPoint.listFiles();
					if (mounts != null)
					{
						for (File driveString : mounts)
						{
							driveStrings.add(driveString.getAbsolutePath());
						}
					}
				}
			}
			return search(driveStrings, directoryStructureToSearchFor);
		}
		else // Assume Unix
		{
			List<String> driveStrings = new ArrayList<String>();
			for (String mountPointString : UNIX_MOUNTS)
			{
				File mountPoint = new File(mountPointString);
				if (mountPoint != null)
				{
					File[] mounts = mountPoint.listFiles();
					if (mounts != null)
					{
						for (File driveString : mountPoint.listFiles())
						{
							driveStrings.add(driveString.getAbsolutePath());
						}
					}
				}
			}
			return search(driveStrings, directoryStructureToSearchFor);
		}
		return new ArrayList<File>();
	}

	/**
	 * Checks each given potential directory for existence of the given
	 * directory structure under it and returns a list of positive matches.
	 * 
	 * @param potentialDirectories
	 *            the potential directories to check.
	 * @param directoryStructure
	 *            the directory structure to search for under each potential
	 *            directory
	 * @return a List<File> containing potentialDirectories which contained the
	 *         given directory structure underneath them
	 */
	public static List<File> search(List<String> potentialDirectories, String directoryStructure)
	{
		ArrayList<File> potentialMatches = new ArrayList<File>();
		for (String potentialTopLevelDirectory : potentialDirectories)
		{
			File odkInstancesDir = new File(potentialTopLevelDirectory + directoryStructure);
			if (odkInstancesDir != null && odkInstancesDir.exists())
			{
				potentialMatches.add(odkInstancesDir);
			}
		}
		return potentialMatches;
	}

	/**
	 * Takes a string representing a path to a file or directory and normalizes
	 * it to use the path separator specified in File.separator.
	 * 
	 * @param pathString
	 *            a String holding the path to normalize
	 * @return a normalized path using File.separator
	 */
	public static String normalizePathString(String pathString)
	{
		int unixIndex = pathString.indexOf("/");
		int windowsIndex = pathString.indexOf("\\");
		if (unixIndex != -1)
		{
			pathString = pathString.replace("/", File.separator);
		}
		if (windowsIndex != -1)
		{
			pathString = pathString.replace("\\", File.separator);
		}
		return pathString;
	}
}
package org.opendatakit.aggregate.upload.utils;

import java.net.URL;
import java.util.logging.Logger;

import org.apache.http.client.HttpClient;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

public class HttpClientFactory {

	/**
	 * Return an http client that is able to communicate with the protocol and
	 * port contained in the given url (http and 80 are assumed if none found).
	 * Set up to be threadsafe and have a 30 second timeout.
	 * 
	 * @param url
	 *            an example url the HttpClient should be able to communicate
	 *            with
	 * @param logger the Logger to use to log messages. May be null.
	 * @return the HttpClient
	 */
	public static HttpClient getHttpClient(URL url, Logger logger)
	{
		// configure connection
        HttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(params, 30000);
        HttpConnectionParams.setSoTimeout(params, 30000);
        HttpClientParams.setRedirecting(params, false);
        // setup client
        SchemeRegistry registry = new SchemeRegistry();
        String scheme = url.getProtocol();
        if(scheme == null)
        {
        	scheme = "http";
        	if (logger != null)
        		logger.info("Assuming protocol to be http.");
        }
        int port = url.getPort();
        if (port == -1)
        {
        	port = 80;
        	if (logger != null)
        		logger.info("Assuming port to be 80.");
        }
        registry.register(new Scheme(scheme, PlainSocketFactory.getSocketFactory(), port));
        ThreadSafeClientConnManager manager = new ThreadSafeClientConnManager(params, registry);
        return new DefaultHttpClient(manager, params);
	}
}
package org.opendatakit.aggregate.upload.submission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.junit.Before;
import org.junit.Test;
import org.opendatakit.aggregate.upload.test.TestUtilities;

public class SubmissionTest 
{
	private static final String VARIABLE_NAME = "${name}";
	private static final String VARIABLE_FILENAME = "${filename}";
	private static final String VARIABLE_MIMETYPE = "${mimetype}";
	private static final String CONTENT_DISPOSITION = String.format("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"", VARIABLE_NAME, VARIABLE_FILENAME);
	private static final String CONTENT_TYPE = String.format("Content-Type: %s", VARIABLE_MIMETYPE);
	
	private Submission _submission;
	
	@Before
	public void setup() throws URISyntaxException, MalformedURLException
	{
		_submission = new Submission(null, null, null, null);
	}
	
	@Test
	public void testCallWithNonNullResponse()
	{
		testCall("non null response");
	}
	
	@Test
	public void testCallWithNullResponse()
	{
		testCall(null);
	}
	
	private void testCall(String response)
	{
		try
		{
			HttpClient mockHttpClient = TestUtilities.getMockHttpClient(response);
			URL url = new URL("http://nowhere.com");
			File submissionDir = TestUtilities.buildSingleSubmission("submission", "submission.xml", "pic1.jpg");
			Submission submission = new Submission(mockHttpClient, url, submissionDir, null);
			SubmissionResult result = submission.call();
			if (response != null)
			{
				assertTrue(result.isSuccess());
			}
			else
			{
				assertTrue(!result.isSuccess());
			}
			assertEquals(url, result.getAggregateURL());
		}
		catch(IOException e)
		{
			fail(e.toString());
		}
	}
	
	@Test
	public void testBuildSubmissionPostWithOneXML()
	{
		testBuildSubmissionPost("http://nowhere.com", "submissionWithOneXML", "submission.xml");
	}
	
	@Test
	public void testBuildSubmissionPostWithOneXMLOneJpg()
	{
		testBuildSubmissionPost("http://nowhere.com", "submissionWithOneXMLTwoPics", "submission.xml", "pic1.jpg");
	}
	
	@Test
	public void testBuildSubmissionPostWithOneXMLOneJpeg()
	{
		testBuildSubmissionPost("http://nowhere.com", "submissionWithOneXMLTwoPics", "submission.xml", "pic1.jpeg");
	}
	
	@Test
	public void testBuildSubmissionPostWithOneXMLOnePng()
	{
		testBuildSubmissionPost("http://nowhere.com", "submissionWithOneXMLTwoPics", "submission.xml", "pic1.png");
	}
	
	@Test
	public void testBuildSubmissionPostWithOneXMLOneJpgOneJpegOnePng()
	{
		testBuildSubmissionPost("http://nowhere.com", "submissionWithOneXMLTwoPics", "submission.xml", "pic1.jpg", "pic2.jpeg", "pic3.png");
	}
	
	@Test
	public void testBuildSubmissionPostWithOneXMLOneHTML()
	{
		testBuildSubmissionPost("http://nowhere.com", "submissionWithOneXMLOneHTML", "submission.xml", "shouldBeIgnored.html");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testBuildSubmissionPostWithTwoXML()
	{
		testBuildSubmissionPost("http://nowhere.com", "submissionWithTwoXML", "submission1.xml", "throwsIllegalArgumentException.xml");
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testBuildSubmissionPostWithNoXML()
	{
		testBuildSubmissionPost("http://nowhere.com", "submissionWithNoXML", "");
	}
	
	private void testBuildSubmissionPost(String submissionURLString, String submissionName, String submissionFileName, String... otherFileNames)
	{
		ByteArrayOutputStream out = null;
		try 
		{
			URL submissionURL = new URL(submissionURLString);
			File submissionFolder = TestUtilities.buildSingleSubmission(submissionName, submissionFileName, otherFileNames);
			HttpPost post = _submission.buildSubmissionPost(submissionURL, submissionFolder);
			HttpEntity entity = post.getEntity();
			out = new ByteArrayOutputStream();
			entity.writeTo(out);
		}
		catch (MalformedURLException e) 
		{
			fail(e.getMessage());
		}
		catch (IOException e) 
		{
			fail(e.getMessage());
		} 
		catch (URISyntaxException e) 
		{
			fail(e.getMessage());
		} 
		String multipartEntity = out.toString();
		assertTrue(multipartEntity.contains(makeContentDispositionString(Submission.FORM_PART_XML_SUBMISSION_FILE, submissionFileName)));
		assertTrue(multipartEntity.contains(makeContentTypeString(Submission.MIME_XML)));
		for (String otherFileName : otherFileNames)
		{
			if (otherFileName.endsWith(Submission.EXTENSION_JPG) || otherFileName.endsWith(Submission.EXTENSION_JPEG))
			{
				assertTrue(multipartEntity.contains(makeContentDispositionString(otherFileName, otherFileName)));
				assertTrue(multipartEntity.contains(makeContentTypeString(Submission.MIME_JPEG)));
			}
			else if (otherFileName.endsWith(Submission.EXTENSION_PNG))
			{
				assertTrue(multipartEntity.contains(makeContentDispositionString(otherFileName, otherFileName)));
				assertTrue(multipartEntity.contains(makeContentTypeString(Submission.MIME_PNG)));				
			}
			else
			{
				assertTrue(!multipartEntity.contains(makeContentDispositionString(otherFileName, otherFileName)));
			}
		}
	}
	
	private String makeContentDispositionString(String name, String filename)
	{
		return CONTENT_DISPOSITION.replace(VARIABLE_NAME, name).
			replace(VARIABLE_FILENAME, filename);		
	}
	
	private String makeContentTypeString(String mimetype)
	{
		return CONTENT_TYPE.replace(VARIABLE_MIMETYPE, mimetype);
	}
}
package org.opendatakit.aggregate.upload.submission;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.apache.http.client.HttpClient;
import org.junit.Before;
import org.junit.Test;
import org.opendatakit.aggregate.upload.test.TestUtilities;

public class SubmissionUploaderTest {

	private static final Random _rand = new Random();
	
	private SubmissionUploader _uploader;
	
	@Before
	public void setUp()
	{
		HttpClient mockHttpClient = TestUtilities.getMockHttpClient("non-null response");
        ExecutorService es = new ThreadPoolExecutor(0, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		_uploader = new SubmissionUploader(mockHttpClient, es);
		_uploader.getLogger().setLevel(Level.OFF);
	}
	
	// Is this a bad test because it relies on the Submission class?
	@Test
	public void testUploadSubmissionWithTwoGoodSubmissions() throws MalformedURLException, InterruptedException, ExecutionException
	{
		testUploadSubmissions(1, 2, "non_null_submission.xml");
	}
	
	@Test
	public void testUploadSubmissionWithTwoBadSubmissions()
	{
		testUploadSubmissions(1, 2, "");
	}
	
	public void testUploadSubmissions(int submissionCountStart, int submissionCountEnd, String submissionXMLName)
	{
		try
		{
	        String submissionPrefix = "submission_";
	        String pic = "pic.jpg";
	        File submissionsParentDir = TestUtilities.buildSubmissionSet(submissionCountStart, submissionCountEnd, submissionPrefix, submissionXMLName, pic);
			URL url = new URL("http://nowhere.com");
						
			List<Future<SubmissionResult>> futures = _uploader.uploadSubmissions(submissionsParentDir, url);
			for (Future<SubmissionResult> future : futures)
			{
				SubmissionResult result = future.get();
				File submissionDir = result.getFile();
				assertTrue(submissionDir.getName().startsWith(submissionPrefix));
				List<String> files = Arrays.asList(submissionDir.list());
				assertEquals(!submissionXMLName.equals(""), files.contains(submissionXMLName));
				assertTrue(files.contains(pic));
				assertEquals(!submissionXMLName.equals(""), result.isSuccess());
			}
		}
		catch (Exception e)
		{
			fail(e.toString());
		}
	}
	
	@Test
	public void testCheckSubmissionResultsAndDeleteSubmissionsWithAllSuccessfulSubmissions()
	{
		testCheckSubmissionResultsAndDeleteSubmissions(100);
	}
	
	@Test
	public void testCheckSubmissionResultsAndDeleteSubmissionsWithNoSuccessfulSubmissions()
	{
		testCheckSubmissionResultsAndDeleteSubmissions(0);
	}
	
	@Test
	public void testCheckSubmissionResultsAndDeleteSubmissionsWithHalfSuccessfulSubmissions()
	{
		testCheckSubmissionResultsAndDeleteSubmissions(50);
	}
	
	private void testCheckSubmissionResultsAndDeleteSubmissions(int successProb)
	{
		String submissionPrefix = "submission";
		String submissionXML = "submission.xml";
		String[] otherFiles = new String[]{"pic1.jpg", "pic2.png"};
		File submissionParentDir = TestUtilities.buildSubmissionSet(1, 10, submissionPrefix, submissionXML, otherFiles);
		List<Future<SubmissionResult>> results = buildSubmissionResultSet(submissionParentDir, successProb);
		_uploader.checkSubmissionResultsAndDeleteSubmissions(results);
		for (Future<SubmissionResult> future : results)
		{
			try
			{
				SubmissionResult result = future.get();
				boolean success = result.isSuccess();
				if (successProb == 100)
					assertTrue(success);
				else if (successProb == 0)
					assertTrue(!success);
				
				if (success)
					assertTrue(!result.getFile().exists());
				else
					assertTrue(result.getFile().exists());
			}
			catch (Exception e)
			{
				fail(e.toString());
			}
		}
	}

	/**
	 * 
	 * @param submissionsParentDir the parent dir containing all of the submission directories under it.
	 * @param probOfSuccess percentage probability of the SubmissionResult.isSuccess() returning true (out of 100%).
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<Future<SubmissionResult>> buildSubmissionResultSet(File submissionsParentDir, int probOfSuccess)
	{
		List<Future<SubmissionResult>> futures = new ArrayList<Future<SubmissionResult>>();
		File[] submissionFolders = submissionsParentDir.listFiles(new FileFilter() {
				
			public boolean accept(File pathname) {
				return pathname.isDirectory();
			}
		});
       	for (File submissionFolder : submissionFolders)
       	{
       		int newInt = _rand.nextInt(100);
       		boolean success = (newInt <= (probOfSuccess - 1));
			SubmissionResult result = new SubmissionResult(submissionFolder, null, success);
			Future<SubmissionResult> mockFuture = mock(Future.class);
			try
			{
				when(mockFuture.get()).thenReturn(result);
			}
			catch (Exception e)
			{
				fail(e.toString());
			}
			futures.add(mockFuture);
       	}
		return futures;
	}
}
package org.opendatakit.aggregate.upload.test;

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.junit.Ignore;

@Ignore
public class TestUtilities {
	
	public static File createTemporaryDirectory()
	{
		try
		{
			File tempDir = File.createTempFile("temp", Long.toString(System.nanoTime()));
		    if(!(tempDir.delete()))
		    {
		        throw new IOException("Could not delete temp file: " + tempDir.getAbsolutePath());
		    }
	
		    if(!(tempDir.mkdir()))
		    {
		        throw new IOException("Could not create temp directory: " + tempDir.getAbsolutePath());
		    }
		    return tempDir;
		}
		catch (IOException e)
		{
			fail(e.toString());
		}
		return null;
	}

	/**
	 * Same as buildSubmission, except a new temporary directory is
	 * automatically created to hold the submission and the file returned is the
	 * submission directory instead of the parent dir.
	 * 
	 * @param submissionName
	 *            the name of the folder to contain the submission
	 * @param submissionXMLName
	 *            the name of the XML submission file (should end in .xml) -
	 *            ends up in "submissionName/submissionXMLName".
	 * @param otherFileNames
	 *            names of other files to create in the submission dir (e.g.
	 *            "pic.jpg", "randomfile.exe")
	 * @return a File representing the newly created submission
	 * @throws IOException
	 */
	public static File buildSingleSubmission(String submissionName, String submissionXMLName, String... otherFileNames)
	{
		File parentDirectory = buildSubmission(createTemporaryDirectory(), submissionName, submissionXMLName, otherFileNames);
		return new File(parentDirectory.getAbsolutePath() + File.separator + submissionName);
	}
	
	/**
	 * Builds a submission under the specified parentDirectory.
	 * 
	 * @param parentDirectory
	 *            the directory to create the submission under
	 * @param submissionName
	 *            the name of the folder to contain the submission
	 * @param submissionXMLName
	 *            the name of the XML submission file (should end in .xml) -
	 *            ends up in "submissionName/submissionXMLName".
	 * @param otherFileNames
	 *            names of other files to create in the submission dir (e.g.
	 *            "pic.jpg", "randomfile.exe")
	 * @return the parentDirectory
	 * @throws IOException
	 */
	public static File buildSubmission(File parentDirectory, String submissionName, String submissionXMLName, String... otherFileNames)
	{
		try
		{
			if (!parentDirectory.exists())
			{
				throw new IllegalArgumentException("parentDirectory: " + parentDirectory + " must exist already.");
			}
			else
			{
				File submissionFolder = new File(parentDirectory.getAbsolutePath() + File.separator + submissionName);
				submissionFolder.mkdir();
				File submissionXMLFile = new File(submissionFolder.getAbsolutePath() + File.separator + submissionXMLName);
				submissionXMLFile.createNewFile();
				for (String otherFileName : otherFileNames)
				{
					File otherFile = new File(submissionFolder.getAbsolutePath() + File.separator + otherFileName);
					otherFile.createNewFile();
				}
			}
		}
		catch (IOException e)
		{
			fail(e.toString());
		}
		return parentDirectory;
	}

	/**
	 * Builds a set of submissions under a freshly created temporary directory.
	 * Returns this directory, the submissions parent directory (i.e. the
	 * directory containing all the submission directories)
	 * 
	 * @param submissionCountStart the start of the count for submissions
	 * @param submissionCountEnd the end of the count for submissions
	 * @param submissionNamePrefix the prefix to use to name all the submissions (submission names go from 'submissionPrefix + submissionCountStart' --> 'submissionPrefix + submissionCountEnd')
	 * @param submissionXMLName the name of the submission XML file to create in each submission (should end in .xml)
	 * @param otherFileNames other files to create under each submission directory
	 * @return a File representing the submission parent directory
	 */
	public static File buildSubmissionSet(int submissionCountStart, int submissionCountEnd, String submissionNamePrefix, String submissionXMLName, String... otherFileNames)
	{
		File tempDir = TestUtilities.createTemporaryDirectory();
		for (int i = submissionCountStart; i <= submissionCountEnd; i++)
		{
			TestUtilities.buildSubmission(tempDir, submissionNamePrefix + i, submissionXMLName, otherFileNames);
		}
		return tempDir;
	}

	/**
	 * Returns a mock HttpClient, which returns the given response when
	 * HttpClient.execute(HttpUriRequest, ResponseHandler) is called
	 * 
	 * @param response
	 *            the response to return
	 * @return a mock HttpClient
	 * @throws IOException 
	 * @throws ClientProtocolException 
	 */
	@SuppressWarnings("unchecked")
	public static HttpClient getMockHttpClient(String response)
	{
		HttpClient mockHttpClient = null;
		try
		{
			mockHttpClient = mock(HttpClient.class);
			when(mockHttpClient.execute(any(HttpUriRequest.class), any(ResponseHandler.class))).thenReturn(response);
		}
		catch(Exception e)
		{
			fail(e.toString());
		}
		return mockHttpClient;
	}
}
package org.opendatakit.aggregate.upload.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

public class BaseURLFinderTest {
	@Test
	public void testGetBaseURLGivenBaseURL()
	{
		testSingleGetBaseURL("http://www.google.com", 
			"http://www.google.com");
	}
	
	@Test 
	public void testGetBaseURLGivenURLWithPath()
	{
		testSingleGetBaseURL("http://www.google.com/something",
			"http://www.google.com");
	}
	
	@Test
	public void testGetBaseURLGivenURLWithExtendedPath()
	{
		testSingleGetBaseURL("http://www.google.com/something/else/is/here",
			"http://www.google.com");
	}
	
	@Test
	public void testGetBaseURLGivenURLWithHttpsAndPortAndParam()
	{
		testSingleGetBaseURL("https://www.google.com:80/?p=param",
				"https://www.google.com:80");
	}
	
	private void testSingleGetBaseURL(String url, String base)
	{
		try
		{
			URL baseURL = new URL(base);
			URL urlToTest = new URL(url);
			assertEquals(baseURL, BaseURLFinder.getBaseURL(urlToTest));
		}
		catch (MalformedURLException e)
		{
			fail("Malformed URL: " + e.getMessage());
		}
	}
}
package org.opendatakit.aggregate.upload.utils;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.opendatakit.aggregate.upload.test.TestUtilities;

public class DeleteDirectoryTest {
	
	@Test
	public void testDeleteDirectoryWithOneFile() throws IOException
	{
		File tempDir = TestUtilities.createTemporaryDirectory();
		File subFile = new File(tempDir.getAbsolutePath() + "/someFile");
		subFile.createNewFile();
		
		assertTrue(DeleteDirectory.deleteDirectory(tempDir));
		assertTrue(!tempDir.exists());
	}
	
	@Test
	public void testDeleteDirectoryWithOneFolder() throws IOException
	{
		File tempDir = TestUtilities.createTemporaryDirectory();
		File subDir = new File(tempDir.getAbsolutePath() + "/someFolder");
		subDir.mkdir();
		
		assertTrue(DeleteDirectory.deleteDirectory(tempDir));
		assertTrue(!tempDir.exists());
	}
	
	@Test
	public void testDeleteDirectoryWithNestedStructure() throws IOException
	{
		File tempDir = TestUtilities.createTemporaryDirectory();
		String tempDirPath = tempDir.getAbsolutePath();
		for (int i = 1; i < 5; i++)
		{
			for (int j = 1; j < 5; j++)
			{
				String deepSubDirString = String.format("/someFolder%d/someFolder%d/someFolder", i, j);
				File deepSubDir = new File(tempDirPath + deepSubDirString);
				deepSubDir.mkdirs();
				File deepFile = new File(tempDirPath + deepSubDirString + "/somFile");
				deepFile.createNewFile();
			}
		}
		
		assertTrue(DeleteDirectory.deleteDirectory(tempDir));
		assertTrue(!tempDir.exists());
	}
}
package org.opendatakit.aggregate.upload.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.opendatakit.aggregate.upload.test.TestUtilities;

public class FindDirectoryStructureTest {
	
	private String _directoryStructure = "/odk/instances";
	
	@Test
	public void testSearchForSubmissionParentDirs() throws IOException
	{
		// Set up ODK Collect submissions structure
		File tempMountDir = TestUtilities.createTemporaryDirectory();
		File tempSubmissionsParentDir = new File(tempMountDir.getAbsolutePath() + _directoryStructure);
		tempSubmissionsParentDir.mkdirs();
		
		// Set up more without the ODK submissions structure
		File tempMountDir2 = TestUtilities.createTemporaryDirectory();
		File tempMountDir3 = TestUtilities.createTemporaryDirectory();
		
		List<String> fakeMountPointsToSearch = new ArrayList<String>();
		fakeMountPointsToSearch.add(tempMountDir.getAbsolutePath());
		fakeMountPointsToSearch.add(tempMountDir2.getAbsolutePath());
		fakeMountPointsToSearch.add(tempMountDir3.getAbsolutePath());
		
		List<File> submissionsParentDirs = FindDirectoryStructure.search(fakeMountPointsToSearch, _directoryStructure);
		
		assertEquals(1, submissionsParentDirs.size());
		assertTrue(submissionsParentDirs.contains(tempSubmissionsParentDir));
	}
	
	@Test
	public void testNormalizePathStringGivenUnixPath()
	{
		String pathString = "/some/unix/path"; // has 3 path separators
		testNormalizePathString(pathString, 3);
	}
	
	@Test
	public void testNormalizePathStringGivenWindowsPath()
	{
		String pathString = "C:\\some\\windows\\path"; // has 3 path separators
		testNormalizePathString(pathString, 3);
	}
	
	@Test
	public void testNormalizePathStringGivenNonPathString()
	{
		String nonPathString = "some string";
		testNormalizePathString(nonPathString, 0);
	}
	
	private void testNormalizePathString(String pathString, int expectedSeparatorCount)
	{
		pathString = FindDirectoryStructure.normalizePathString(pathString);
		int actualSeparatorCount = 0;
		for (char c : pathString.toCharArray())
		{
			if (c == File.separatorChar)
			{
				actualSeparatorCount++;
			}
		}
		assertEquals(expectedSeparatorCount, actualSeparatorCount);
	}
}
