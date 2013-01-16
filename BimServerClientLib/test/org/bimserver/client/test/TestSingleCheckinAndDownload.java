package org.bimserver.client.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.activation.DataHandler;
import javax.activation.FileDataSource;

import org.apache.commons.io.IOUtils;
import org.bimserver.client.BimServerClient;
import org.bimserver.client.BimServerClientFactory;
import org.bimserver.client.ChannelConnectionException;
import org.bimserver.client.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SActionState;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SLongActionState;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.interfaces.objects.SSerializerPluginConfiguration;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.ServiceException;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.shared.interfaces.ServiceInterface;

public class TestSingleCheckinAndDownload {
	public static void main(String[] args) {
		test();
	}

	private static void test() {
		try {
			// Create a BimServerClientFactory, change Json to ProtocolBuffers or Soap if you like
			BimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080");
			
			// Create a new BimServerClient with authentication
			BimServerClient bimServerClient = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"));
			
			// When you use the channel for checkin/download calls, all data will have to be converted to the used channel's format, for example for JSON, binary data will be Base64 encoded, which will make things slower and larger
			// The alternative is to use the Servlets, those will also use compression where possible
			boolean useChannel = false; // Using the channel is slower

			// Get the service interface
			ServiceInterface serviceInterface = bimServerClient.getServiceInterface();
			
			// Create a new project
			SProject newProject = serviceInterface.addProject("test" + Math.random());
			
			// This is the file we will be checking in
			File ifcFile = new File("../TestData/data/AC11-FZK-Haus-IFC.ifc");
			
			// Find a deserializer to use
			SDeserializerPluginConfiguration deserializer = serviceInterface.getSuggestedDeserializerForExtension("ifc");
			
			// Checkin
			Long stateId = -1L;
			if (useChannel) {
				stateId = serviceInterface.checkin(newProject.getOid(), "test", deserializer.getOid(), ifcFile.length(), ifcFile.getName(), new DataHandler(new FileDataSource(ifcFile)), false, true);
			} else {
				stateId = bimServerClient.checkin(newProject.getOid(), "test", deserializer.getOid(), false, true, ifcFile);
			}
			
			// Get the status
			SLongActionState longActionState = serviceInterface.getLongActionState(stateId);
			if (longActionState.getState() == SActionState.FINISHED) {
				// Find a serializer
				SSerializerPluginConfiguration serializer = serviceInterface.getSerializerByContentType("application/ifc");
				
				// Get the project details
				newProject = serviceInterface.getProjectByPoid(newProject.getOid());
				
				// Download the latest revision  (the one we just checked in)
				if (useChannel) {
					Long downloadId = serviceInterface.download(newProject.getLastRevisionId(), serializer.getOid(), true, true);
					SLongActionState downloadState = serviceInterface.getLongActionState(downloadId);
					if (downloadState.getState() == SActionState.FINISHED) {
						InputStream inputStream = serviceInterface.getDownloadData(downloadId).getFile().getInputStream();
						IOUtils.copy(inputStream, new ByteArrayOutputStream());
						System.out.println("Success");
					}
				} else {
					Long downloadId = serviceInterface.download(newProject.getLastRevisionId(), serializer.getOid(), true, false); // Note: sync: false
					InputStream downloadData = bimServerClient.getDownloadData(downloadId, serializer.getOid());
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					IOUtils.copy(downloadData, baos);
					System.out.println(baos.size() + " bytes downloaded");
				}
			} else {
				System.out.println(longActionState.getState());
			}
		} catch (ChannelConnectionException e) {
			e.printStackTrace();
		} catch (ServerException e) {
			e.printStackTrace();
		} catch (UserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ServiceException e) {
			e.printStackTrace();
		}
	}
}