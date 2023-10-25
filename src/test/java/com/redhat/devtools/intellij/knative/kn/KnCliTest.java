/*******************************************************************************
 * Copyright (c) 2021 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc.
 ******************************************************************************/
package com.redhat.devtools.intellij.knative.kn;

import com.intellij.execution.process.ProcessListener;
import com.intellij.terminal.TerminalExecutionConsole;
import com.redhat.devtools.intellij.common.model.ProcessHandlerInput;
import com.redhat.devtools.intellij.common.utils.CommonTerminalExecutionConsole;
import com.redhat.devtools.intellij.common.utils.ExecHelper;
import com.redhat.devtools.intellij.common.utils.ExecProcessHandler;
import com.redhat.devtools.intellij.knative.BaseTest;
import com.redhat.devtools.intellij.knative.ui.createFunc.CreateFuncModel;
import com.redhat.devtools.intellij.knative.utils.model.GitRepoModel;
import com.redhat.devtools.intellij.knative.utils.model.ImageRegistryModel;
import com.redhat.devtools.intellij.knative.utils.model.InvokeModel;
import io.fabric8.kubernetes.api.model.RootPaths;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class KnCliTest extends BaseTest {

    private static final String RESOURCES_PATH = "kn/";

    private KubernetesClient kubernetesClient;
    private RootPaths rootPaths;
    private URL masterURL;

    public void setUp() throws Exception {
        super.setUp();
        kn = mock(KnCli.class, CALLS_REAL_METHODS);
        kubernetesClient = mock(KubernetesClient.class);
        rootPaths = mock(RootPaths.class);

        Field clientField = KnCli.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(kn, kubernetesClient);
        Field knCommandField = KnCli.class.getDeclaredField("knCommand");
        knCommandField.setAccessible(true);
        knCommandField.set(kn, "knCommand");
        Field funcCommandField = KnCli.class.getDeclaredField("funcCommand");
        funcCommandField.setAccessible(true);
        funcCommandField.set(kn, "funcCommand");
        Field envField = KnCli.class.getDeclaredField("envVars");
        envField.setAccessible(true);
        envField.set(kn, Collections.emptyMap());

        when(kubernetesClient.rootPaths()).thenReturn(rootPaths);

        masterURL = new URL("http://url.ext");
        when(kubernetesClient.getMasterUrl()).thenReturn(masterURL);
    }

    public void testIsKnativeServingAware_ClientFails_Throws() {
        when(rootPaths.getPaths()).thenThrow(new KubernetesClientException("error"));
        try {
            kn.isKnativeServingAware();
        } catch (IOException e) {
            assertEquals("io.fabric8.kubernetes.client.KubernetesClientException: error", e.getLocalizedMessage());
        }
    }

    public void testIsKnativeServingAware_ClusterHasKnativeServing_True() throws IOException {
        when(rootPaths.getPaths()).thenReturn(Arrays.asList("serving.knative.dev", "type"));
        boolean result = kn.isKnativeServingAware();
        assertTrue(result);
    }

    public void testIsKnativeServingAware_ClusterHasNotKnativeServing_False() throws IOException {
        when(rootPaths.getPaths()).thenReturn(Arrays.asList("sometype", "type2"));
        boolean result = kn.isKnativeServingAware();
        assertFalse(result);
    }

    public void testIsKnativeEventingAware_ClientFails_Throws() {
        when(rootPaths.getPaths()).thenThrow(new KubernetesClientException("error"));
        try {
            kn.isKnativeEventingAware();
        } catch (IOException e) {
            assertEquals("io.fabric8.kubernetes.client.KubernetesClientException: error", e.getLocalizedMessage());
        }
    }

    public void testIsKnativeEventingAware_ClusterHasKnativeEventing_True() throws IOException {
        when(rootPaths.getPaths()).thenReturn(Arrays.asList("eventing.knative.dev", "type"));
        boolean result = kn.isKnativeEventingAware();
        assertTrue(result);
    }

    public void testIsKnativeEventingAware_ClusterHasNotKnativeEventing_False() throws IOException {
        when(rootPaths.getPaths()).thenReturn(Arrays.asList("sometype", "type2"));
        boolean result = kn.isKnativeEventingAware();
        assertFalse(result);
    }

    public void testGetMasterURL_URL() {
        assertEquals(masterURL, kn.getMasterUrl());
    }

    public void testGetNamespace_NamespaceIsEmpty_Default() {
        when(kubernetesClient.getNamespace()).thenReturn("");
        assertEquals("default", kn.getNamespace());
    }

    public void testGetNamespace_NamespaceExists_Namespace() {
        when(kubernetesClient.getNamespace()).thenReturn("namespace");
        assertEquals("namespace", kn.getNamespace());
    }

    public void testGetServicesList_ClusterHasNoServices_EmptyList() throws IOException {
        ExecHelper.ExecResult execResult = new ExecHelper.ExecResult("No services found.", null, 0);
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.executeWithResult(anyString(), anyMap(), any())).thenReturn(execResult);
            assertEquals(Collections.emptyList(), kn.getServicesList());
        }
    }

    public void testGetServicesList_ClusterHasServices_ListOfServices() throws IOException {
        String servicesListInJson = load(RESOURCES_PATH + "serviceslist.json");
        ExecHelper.ExecResult execResult = new ExecHelper.ExecResult(servicesListInJson, null, 0);
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.executeWithResult(anyString(), anyMap(), any())).thenReturn(execResult);
            List<Service> serviceList = kn.getServicesList();
            assertEquals(1, serviceList.size());
            assertEquals("test", serviceList.get(0).getName());
        }
    }

    public void testGetServicesList_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.executeWithResult(anyString(), anyMap(), any())).thenThrow(new IOException("error"));
            kn.getServicesList();
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testGetRevisionsForService_ServiceHasNoRevisions_EmptyList() throws IOException {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn("No revisions found.");
            assertEquals(Collections.emptyList(), kn.getRevisionsForService("test"));
        }
    }

    public void testGetRevisionsForService_ServiceHasRevisions_ListOfRevisions() throws IOException {
        String servicesListInJson = load(RESOURCES_PATH + "revisionsList.json");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn(servicesListInJson);
            List<Revision> revisionList = kn.getRevisionsForService("test");
            assertTrue(revisionList.size() == 2);
            assertEquals("test-00002", revisionList.get(0).getName());
            assertEquals("test-00001", revisionList.get(1).getName());
        }
    }

    public void testGetRevisionsForService_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenThrow(new IOException("error"));
            kn.getRevisionsForService("test");
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testGetFunctions_ClusterHasNoFunctions_EmptyList() throws IOException {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn("No functions found.");
            assertEquals(Collections.emptyList(), kn.getFunctions());
        }
    }

    public void testGetFunctions_ClusterHasRevisions_ListOfFunctions() throws IOException {
        String functionsListInJson = load(RESOURCES_PATH + "functionsList.json");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn(functionsListInJson);
            List<Function> functionList = kn.getFunctions();
            assertTrue(functionList.size() == 1);
            assertEquals("myfunc", functionList.get(0).getName());
        }
    }

    public void testGetFunctions_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenThrow(new IOException("error"));
            kn.getFunctions();
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testGetService_NameIsValid_Service() throws IOException {
        String serviceInJson = load(RESOURCES_PATH + "service.json");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn(serviceInJson);
            Service service = kn.getService("test");
            assertEquals("test", service.getName());
        }
    }

    public void testGetService_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenThrow(new IOException("error"));
            kn.getService("test");
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testGetServiceYAML_NameIsValid_ServiceYAML() throws IOException {
        String serviceInYaml = load(RESOURCES_PATH + "service.yaml");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn(serviceInYaml);
            assertEquals(serviceInYaml, kn.getServiceYAML("test"));
        }
    }

    public void testGetServiceYAML_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenThrow(new IOException("error"));
            kn.getServiceYAML("test");
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testGetRevisionYAML_NameIsValid_RevisionYAML() throws IOException {
        String revisionInYaml = load(RESOURCES_PATH + "revision.yaml");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn(revisionInYaml);
            assertEquals(revisionInYaml, kn.getRevisionYAML("test"));
        }
    }

    public void testGetRevisionYAML_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenThrow(new IOException("error"));
            kn.getRevisionYAML("test");
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testDeleteServices_OneServiceToBeDeleted_DeletionIsCalled() throws IOException {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.deleteServices(Arrays.asList("one"));
            execHelperMockedStatic.verify(() ->
                    ExecHelper.execute(anyString(), anyMap(), eq("service"), eq("delete"), eq("one"), eq("-n"), eq("default")));
        }
    }

    public void testDeleteServices_MultipleServicesToBeDeleted_DeletionIsCalled() throws IOException {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.deleteServices(Arrays.asList("one", "two", "three"));
            execHelperMockedStatic.verify(() ->
                    ExecHelper.execute(anyString(), anyMap(), eq("service"), eq("delete"), eq("one"),
                            eq("two"), eq("three"), eq("-n"), eq("default")));
        }
    }

    public void testDeleteServices_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenThrow(new IOException("error"));
            kn.deleteServices(Collections.emptyList());
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testDeleteRevisions_OneRevisionToBeDeleted_DeletionIsCalled() throws IOException {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.deleteRevisions(Arrays.asList("one"));
            execHelperMockedStatic.verify(() ->
                    ExecHelper.execute(anyString(), anyMap(), eq("revision"), eq("delete"), eq("one"), eq("-n"), eq("default")));
        }
    }

    public void testDeleteRevisions_MultipleRevisionsToBeDeleted_DeletionIsCalled() throws IOException {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.deleteRevisions(Arrays.asList("one", "two", "three"));
            execHelperMockedStatic.verify(() ->
                    ExecHelper.execute(anyString(), anyMap(), eq("revision"), eq("delete"), eq("one"),
                            eq("two"), eq("three"), eq("-n"), eq("default")));
        }
    }

    public void testDeleteRevisions_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenThrow(new IOException("error"));
            kn.deleteRevisions(Collections.emptyList());
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testDeleteFunctions_OneFunctionToBeDeleted_DeletionIsCalled() throws IOException {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.deleteFunctions(Arrays.asList("one"));
            execHelperMockedStatic.verify(() ->
                    ExecHelper.execute(anyString(), anyMap(), eq("delete"), eq("one"), eq("-n"), eq("default")));
        }
    }

    public void testDeleteFunctions_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenThrow(new IOException("error"));
            kn.deleteFunctions(Collections.emptyList());
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testGetSources_ClusterHasSources_ListOfSources() throws IOException {
        String serviceInJson = load(RESOURCES_PATH + "sourcesList.json");
        ExecHelper.ExecResult execResult = new ExecHelper.ExecResult(serviceInJson, null, 0);
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.executeWithResult(anyString(), anyMap(), any())).thenReturn(execResult);
            List<Source> sources = kn.getSources();
            assertEquals(1, sources.size());
            assertEquals("example-source-apiserver0", sources.get(0).getName());
        }
    }

    public void testGetSources_ClusterHasNoServices_EmptyList() throws IOException {
        ExecHelper.ExecResult execResult = new ExecHelper.ExecResult("No sources found.", null, 0);
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.executeWithResult(anyString(), anyMap(), any())).thenReturn(execResult);
            List<Source> sources = kn.getSources();
            assertEquals(Collections.emptyList(), sources);
        }
    }

    public void testCreateFunc_PathIsInserted_RunIsCalled() throws IOException {
        CreateFuncModel model = new CreateFuncModel("path", "runtime", "template");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.createFunc(model);
            execHelperMockedStatic.verify(() ->
                    ExecHelper.execute(anyString(), anyMap(), eq("create"), eq("path"), eq("-l"), eq("runtime"), eq("-t"), eq("template"), eq("-n"), anyString()));
        }
    }

    public void testCreateFunc_ClientFails_Throws() {
        CreateFuncModel model = new CreateFuncModel("path", "runtime", "template");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(any(), anyString())).thenThrow(new IOException("error"));
            kn.createFunc(model);
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testBuildFunc_RegistryIsInsertedButNoImage_BuildIsCalled() throws IOException {
        TerminalExecutionConsole terminalExecutionConsole = mock(TerminalExecutionConsole.class);
        ProcessListener processListener = mock(ProcessListener.class);
        java.util.function.Function<ProcessHandlerInput, ExecProcessHandler> processHandlerFunction = mock(java.util.function.Function.class);
        ImageRegistryModel model = new ImageRegistryModel("", "registry");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.buildFunc("path", model, terminalExecutionConsole, processHandlerFunction, processListener);
            execHelperMockedStatic.verify(() ->
                    ExecHelper.executeWithTerminal(eq(null), anyString(), anyMap(),
                            any(TerminalExecutionConsole.class), any(java.util.function.Function.class), any(ProcessListener.class), anyString(),
                            eq("build"), eq("-r"), eq("registry"), eq("-p"), eq("path"),
                            eq("-v")));
        }
    }

    public void testBuildFunc_ImageIsInserted_BuildIsCalled() throws IOException {
        TerminalExecutionConsole terminalExecutionConsole = mock(TerminalExecutionConsole.class);
        ProcessListener processListener = mock(ProcessListener.class);
        java.util.function.Function<ProcessHandlerInput, ExecProcessHandler> processHandlerFunction = mock(java.util.function.Function.class);
        ImageRegistryModel model = new ImageRegistryModel("image", "registry");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.buildFunc("path", model, terminalExecutionConsole, processHandlerFunction, processListener);
            execHelperMockedStatic.verify(() ->
                    ExecHelper.executeWithTerminal(eq(null), anyString(), anyMap(),
                            any(TerminalExecutionConsole.class), any(java.util.function.Function.class), any(ProcessListener.class), anyString(),
                            eq("build"), eq("-i"), eq("image"), eq("-p"), eq("path"),
                            eq("-v")));
        }
    }

    public void testBuildFunc_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.executeWithTerminal(any(), anyString())).thenThrow(new IOException("error"));
            kn.buildFunc("", new ImageRegistryModel(), null, null, null);
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testDeployFunc_RegistryIsInsertedButNoImage_DeployIsCalled() throws IOException {
        TerminalExecutionConsole terminalExecutionConsole = mock(TerminalExecutionConsole.class);
        ProcessListener processListener = mock(ProcessListener.class);
        ImageRegistryModel model = new ImageRegistryModel("", "registry");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.deployFunc("namespace", "path", model, terminalExecutionConsole, processListener);
            execHelperMockedStatic.verify(() ->
                    ExecHelper.executeWithTerminal(any(), anyString(), anyMap(), eq(terminalExecutionConsole), eq(processListener), anyString(), eq("deploy"), eq("-r"), eq("registry"), eq("-n"), eq("namespace"), eq("-p"), eq("path"), eq("-v"), eq("--build"), eq("false")));
        }
    }

    public void testDeployFunc_ImageIsInserted_DeployIsCalled() throws IOException {
        TerminalExecutionConsole terminalExecutionConsole = mock(TerminalExecutionConsole.class);
        ProcessListener processListener = mock(ProcessListener.class);
        ImageRegistryModel model = new ImageRegistryModel("image", "registry");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.deployFunc("namespace", "path", model, terminalExecutionConsole, processListener);
            execHelperMockedStatic.verify(() ->
                    ExecHelper.executeWithTerminal(any(), anyString(), anyMap(), eq(terminalExecutionConsole), eq(processListener), anyString(), eq("deploy"), eq("-i"), eq("image"), eq("-n"), eq("namespace"), eq("-p"), eq("path"), eq("-v"), eq("--build"), eq("false")));
        }
    }

    public void testDeployFunc_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.executeWithTerminal(any(), anyString())).thenThrow(new IOException("error"));
            kn.deployFunc("", "", new ImageRegistryModel(), null, null);
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testOnClusterBuildFunc_ImageIsPassed_DeployIsCalled() throws IOException {
        TerminalExecutionConsole terminalExecutionConsole = mock(TerminalExecutionConsole.class);
        ProcessListener processListener = mock(ProcessListener.class);
        ImageRegistryModel model = new ImageRegistryModel("image", "");
        GitRepoModel repoModel = new GitRepoModel("repo");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.onClusterBuildFunc("namespace", "path", repoModel, model, terminalExecutionConsole, processListener);
            execHelperMockedStatic.verify(() ->
                    ExecHelper.executeWithTerminal(any(), anyString(), anyMap(), eq(terminalExecutionConsole),
                            eq(processListener), anyString(), eq("deploy"), eq("-i"), eq("image"),
                            eq("-n"), eq("namespace"), eq("-p"), eq("path"),  eq("--remote"),
                            eq("--git-url"), eq("repo"), eq("-v")));
        }
    }

    public void testOnClusterBuildFunc_AutoDiscoveryIsUsed_DeployIsCalled() throws IOException {
        TerminalExecutionConsole terminalExecutionConsole = mock(TerminalExecutionConsole.class);
        ProcessListener processListener = mock(ProcessListener.class);
        ImageRegistryModel model = new ImageRegistryModel();
        model.setAutoDiscovery();
        GitRepoModel repoModel = new GitRepoModel("repo");
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.onClusterBuildFunc("namespace", "path", repoModel, model, terminalExecutionConsole, processListener);
            execHelperMockedStatic.verify(() ->
                    ExecHelper.executeWithTerminal(any(), anyString(), anyMap(), eq(terminalExecutionConsole),
                            eq(processListener), anyString(), eq("deploy"), eq("-n"), eq("namespace"),
                            eq("-p"), eq("path"),  eq("--remote"),
                            eq("--git-url"), eq("repo"), eq("-v")));
        }
    }

    public void testOnClusterBuildFunc_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.executeWithTerminal(any(), anyString())).thenThrow(new IOException("error"));
            kn.onClusterBuildFunc("", "", null, new ImageRegistryModel(), null, null);
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testInvokeFunc_ResultIsNull_Throw() {
        InvokeModel model = new InvokeModel();
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn(null);
            kn.invokeFunc(model);
        } catch (IOException e) {
            assertTrue(e.getLocalizedMessage().startsWith("Failed to retrieve invoke execution ID."));
        }
    }

    public void testInvokeFunc_ResultIsEmpty_Throw() {
        InvokeModel model = new InvokeModel();
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn("");
            kn.invokeFunc(model);
        } catch (IOException e) {
            assertTrue(e.getLocalizedMessage().startsWith("Failed to retrieve invoke execution ID."));
        }
    }

    public void testInvokeFunc_ResultNotHaveID_Throw() {
        InvokeModel model = new InvokeModel();
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn("{\"content\":\"test\"}");
            kn.invokeFunc(model);
        } catch (IOException e) {
            assertTrue(e.getLocalizedMessage().startsWith("Failed to retrieve invoke execution ID."));
        }
    }

    public void testInvokeFunc_ResultHaveID_ID() throws IOException {
        InvokeModel model = new InvokeModel();
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.execute(anyString(), anyMap(), any())).thenReturn("{\"ID\":\"test\"}");
            String id = kn.invokeFunc(model);
            assertEquals("test", id);
        }
    }

    public void testRunFunc_PathIsInserted_RunIsCalled() throws IOException {
        ProcessListener processListener = mock(ProcessListener.class);
        java.util.function.Function<ProcessHandlerInput, ExecProcessHandler> processHandlerFunction = mock(java.util.function.Function.class);
        CommonTerminalExecutionConsole commonTerminalExecutionConsole = mock(CommonTerminalExecutionConsole.class);
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            kn.runFunc("path", commonTerminalExecutionConsole, processHandlerFunction, processListener);
            execHelperMockedStatic.verify(() ->
                    ExecHelper.executeWithTerminal(eq(null), anyString(), anyMap(),
                            any(CommonTerminalExecutionConsole.class), any(java.util.function.Function.class),
                            any(ProcessListener.class), anyString(), eq("run"), eq("-p"), eq("path"),
                            eq("-b=false")
                    ));
        }
    }

    public void testRunFunc_ClientFails_Throws() {
        try (MockedStatic<ExecHelper> execHelperMockedStatic = mockStatic(ExecHelper.class)) {
            execHelperMockedStatic.when(() -> ExecHelper.executeWithTerminal(any(), anyString())).thenThrow(new IOException("error"));
            kn.runFunc("", null, null, null);
        } catch (IOException e) {
            assertEquals("error", e.getLocalizedMessage());
        }
    }

    public void testGetFuncFile_FileExists_File() throws IOException {
        File file = kn.getFuncFile(getPath(RESOURCES_PATH + "func/"));
        assertTrue(file.exists());
    }

    public void testGetFuncFile_FileNotExists_Throws() {
        try {
            kn.getFuncFile(getPath(RESOURCES_PATH));
        } catch(IOException e) {
            assertTrue(e.getLocalizedMessage().equalsIgnoreCase("No func.yaml file found"));
        }

    }
}
