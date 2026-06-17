package com.aaelevator.qbintegration.service;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.jws.soap.SOAPBinding;

@WebService(name = "QBWebConnectorSvc",
        targetNamespace = "http://developer.intuit.com/")
@SOAPBinding(style = SOAPBinding.Style.DOCUMENT,
        use = SOAPBinding.Use.LITERAL,
        parameterStyle = SOAPBinding.ParameterStyle.WRAPPED)
public interface QBWebConnectorService {

    @WebMethod
    @WebResult(name = "authenticateResult", targetNamespace = "http://developer.intuit.com/")
    ArrayOfString authenticate(
            @WebParam(name = "strUserName", targetNamespace = "") String userName,
            @WebParam(name = "strPassword", targetNamespace = "") String password);

    @WebMethod
    @WebResult(name = "sendRequestXMLResult", targetNamespace = "")
    String sendRequestXML(
            @WebParam(name = "ticket", targetNamespace = "") String ticket,
            @WebParam(name = "strHCPResponse", targetNamespace = "") String hcpResponse,
            @WebParam(name = "strCompanyFileName", targetNamespace = "") String companyFileName,
            @WebParam(name = "qbXMLCountry", targetNamespace = "") String country,
            @WebParam(name = "qbXMLMajorVers", targetNamespace = "") int majorVersion,
            @WebParam(name = "qbXMLMinorVers", targetNamespace = "") int minorVersion);

    @WebMethod
    @WebResult(name = "receiveResponseXMLResult", targetNamespace = "")
    int receiveResponseXML(
            @WebParam(name = "ticket", targetNamespace = "") String ticket,
            @WebParam(name = "response", targetNamespace = "") String response,
            @WebParam(name = "hresult", targetNamespace = "") String hresult,
            @WebParam(name = "message", targetNamespace = "") String message);

    @WebMethod
    @WebResult(name = "closeConnectionResult", targetNamespace = "")
    String closeConnection(
            @WebParam(name = "ticket", targetNamespace = "") String ticket);

    @WebMethod
    @WebResult(name = "connectionErrorResult", targetNamespace = "")
    String connectionError(
            @WebParam(name = "ticket", targetNamespace = "") String ticket,
            @WebParam(name = "hresult", targetNamespace = "") String hresult,
            @WebParam(name = "message", targetNamespace = "") String message);

    @WebMethod
    @WebResult(name = "getLastErrorResult", targetNamespace = "")
    String getLastError(
            @WebParam(name = "ticket", targetNamespace = "") String ticket);

    @WebMethod
    @WebResult(name = "serverVersionResult", targetNamespace = "")
    String serverVersion();

    @WebMethod
    @WebResult(name = "clientVersionResult", targetNamespace = "")
    String clientVersion(
            @WebParam(name = "strVersion", targetNamespace = "") String version);
}