 * �����������
 * @author hellogv
 *
 */
public class HttpGetProxy{
    final static public String TAG = "HttpGetProxy";
    /** ���Ӵ��Ķ˿� */
    private int remotePort=-1;
    /** Զ�̷�������ַ */
    private String remoteHost;
    /** ���������ʹ�õĶ˿� */
    private int localPort;
    /** ���ط�������ַ */
    private String localHost;
    private ServerSocket localServer = null;
    /** �շ�Media Player�����Socket */
    private Socket sckPlayer = null;
    /** �շ�Media Server�����Socket */
    private Socket sckServer = null;
      
    private SocketAddress address;
      
    /**�����߳�*/
    private DownloadThread download = null;
    /**
     * ��ʼ�����������
     * 
     * @param localport ��������������Ķ˿�
     */
    public HttpGetProxy(int localport) {
        try {
            localPort = localport;
            localHost = C.LOCAL_IP_ADDRESS;
            localServer = new ServerSocket(localport, 1,InetAddress.getByName(localHost));
        } catch (Exception e) {
            System.exit(0);
        }
    }
  
    /**
     * ��URL��ǰ������SD����ʵ��Ԥ����
     * @param urlString
     * @return ����Ԥ�����ļ���
     * @throws Exception
     */
    public String prebuffer(String urlString,int size) throws Exception{
        if(download!=null && download.isDownloading())
            download.stopThread(true);
          
        URI tmpURI=new URI(urlString);
        String fileName=ProxyUtils.urlToFileName(tmpURI.getPath());
        String filePath=C.getBufferDir()+"/"+fileName;
          
        download=new DownloadThread(urlString,filePath,size);
        download.startThread();
          
        return filePath;
    }
      
    /**
     * ������URLתΪ����URL��127.0.0.1�滻��������
     * 
     * @param url����URL
     * @return [0]:�ض����MP4����URL��[1]:����URL
     */
    public String[] getLocalURL(String urlString) {
          
        // ----�ų�HTTP����----//
        String targetUrl = ProxyUtils.getRedirectUrl(urlString);
        // ----��ȡ��Ӧ���ش��������������----//
        String localUrl = null;
        URI originalURI = URI.create(targetUrl);
        remoteHost = originalURI.getHost();
        if (originalURI.getPort() != -1) {// URL��Port
            address = new InetSocketAddress(remoteHost, originalURI.getPort());// ʹ��Ĭ�϶˿�
            remotePort = originalURI.getPort();// ����˿ڣ���תʱ�滻
            localUrl = targetUrl.replace(
                    remoteHost + ":" + originalURI.getPort(), localHost + ":"
                            + localPort);
        } else {// URL����Port
            address = new InetSocketAddress(remoteHost, C.HTTP_PORT);// ʹ��80�˿�
            remotePort = -1;
            localUrl = targetUrl.replace(remoteHost, localHost + ":"
                    + localPort);
        }
          
        String[] result= new String[]{targetUrl,localUrl};
        return result;
    }
  
    /**
     * �첽�������������
     * 
     * @throws IOException
     */
    public void asynStartProxy() {
  
        new Thread() {
            public void run() {
                startProxy();
            }
        }.start();
    }
  
    private void startProxy() {
        HttpParser httpParser =null;
        int bytes_read;
        boolean enablePrebuffer=false;//�����������
          
        byte[] local_request = new byte[1024];
        byte[] remote_reply = new byte[1024];
  
        while (true) {
            boolean hasResponseHeader = false;
            try {// ��ʼ�µ�request֮ǰ�رչ�ȥ��Socket
                if (sckPlayer != null)
                    sckPlayer.close();
                if (sckServer != null)
                    sckServer.close();
            } catch (IOException e1) {}
            try {
                // --------------------------------------
                // ����MediaPlayer������MediaPlayer->���������
                // --------------------------------------
                sckPlayer = localServer.accept();
                Log.e("TAG","------------------------------------------------------------------");
                if(download!=null && download.isDownloading())
                    download.stopThread(false);
                  
                httpParser=new HttpParser(remoteHost,remotePort,localHost,localPort);
                  
                ProxyRequest request = null;
                while ((bytes_read = sckPlayer.getInputStream().read(local_request)) != -1) {
                    byte[] buffer=httpParser.getRequestBody(local_request,bytes_read);
                    if(buffer!=null){
                        request=httpParser.getProxyRequest(buffer);
                        break;
                    }
                }
                  
                boolean isExists=new File(request._prebufferFilePath).exists();
                enablePrebuffer = isExists && request._isReqRange0;//���߾߱�����ʹ��Ԥ����
                Log.e(TAG,"enablePrebuffer:"+enablePrebuffer);
                sentToServer(request._body);
                // ------------------------------------------------------
                // ������������ķ�������MediaPlayer�����������->���������->MediaPlayer
                // ------------------------------------------------------
                boolean enableSendHeader=true;
                while ((bytes_read = sckServer.getInputStream().read(remote_reply)) != -1) {
                    byte[] tmpBuffer = new byte[bytes_read];
                    System.arraycopy(remote_reply, 0, tmpBuffer, 0, tmpBuffer.length);
                      
                    if(hasResponseHeader){
                    sendToMP(tmpBuffer);
                    }
                    else{
                        List<byte[]> httpResponse=httpParser.getResponseBody(remote_reply, bytes_read);
                        if(httpResponse.size()>0){
                            hasResponseHeader = true;
                            if (enableSendHeader) {
                                // send http header to mediaplayer
                                sendToMP(httpResponse.get(0));
                                String responseStr = new String(httpResponse.get(0));
                                Log.e(TAG+"<---", responseStr);
                            }
                            if (enablePrebuffer) {//send prebuffer to mediaplayer
                                int fileBufferSize = sendPrebufferToMP(request._prebufferFilePath);
                                if (fileBufferSize > 0) {//���·������󵽷�����
                                    String newRequestStr = httpParser.modifyRequestRange(request._body,
                                                    fileBufferSize);
                                    Log.e(TAG + "-pre->", newRequestStr);
                                    enablePrebuffer = false;
  
                                    // �´β�����response��http header
                                    sentToServer(newRequestStr);
                                    enableSendHeader = false;
                                    hasResponseHeader = false;
                                    continue;
                                }
                            }
  
                            //����ʣ������
                            if (httpResponse.size() == 2) {
                                sendToMP(httpResponse.get(1));
                            }
                        }
                    }
                }
                Log.e(TAG, ".........over..........");
  
                // �ر� 2��SOCKET
                sckPlayer.close();
                sckServer.close();
            } catch (Exception e) {
                Log.e(TAG,e.toString());
                Log.e(TAG,ProxyUtils.getExceptionMessage(e));
            }
        }
    }
      
    private int sendPrebufferToMP(String fileName) throws IOException {
        int fileBufferSize=0;
        byte[] file_buffer = new byte[1024];
        int bytes_read = 0;
        FileInputStream fInputStream = new FileInputStream(fileName);
        while ((bytes_read = fInputStream.read(file_buffer)) != -1) {
            fileBufferSize += bytes_read;
            byte[] tmpBuffer = new byte[bytes_read];
            System.arraycopy(file_buffer, 0, tmpBuffer, 0, bytes_read);
            sendToMP(tmpBuffer);
        }
        fInputStream.close();
          
        Log.e(TAG,"��ȡ���...����:"+download.getDownloadedSize()+",��ȡ:"+fileBufferSize);
        return fileBufferSize;
    }
      
    private void sendToMP(byte[] bytes) throws IOException{
            sckPlayer.getOutputStream().write(bytes);
            sckPlayer.getOutputStream().flush();
    }
  
    private void sentToServer(String requestStr) throws IOException{
        try {
            if(sckServer!=null)
                sckServer.close();
        } catch (Exception ex) {}
        sckServer = new Socket();
        sckServer.connect(address);
        sckServer.getOutputStream().write(requestStr.getBytes());// ����MediaPlayer������
        sckServer.getOutputStream().flush();
    }
}