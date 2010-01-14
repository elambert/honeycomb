package com.sun.dtf.cluster.ssh;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import com.sun.dtf.actions.Action;
import com.sun.dtf.exceptions.RemoteCmdException;
import com.sun.dtf.exceptions.SSHException;
import com.sun.dtf.util.ThreadUtil;

import com.sshtools.j2ssh.SshClient;
import com.sshtools.j2ssh.authentication.AuthenticationProtocolException;
import com.sshtools.j2ssh.authentication.AuthenticationProtocolState;
import com.sshtools.j2ssh.authentication.PasswordAuthenticationClient;
import com.sshtools.j2ssh.authentication.PublicKeyAuthenticationClient;
import com.sshtools.j2ssh.authentication.SshAuthenticationClient;
import com.sshtools.j2ssh.authentication.SshAuthenticationPrompt;
import com.sshtools.j2ssh.configuration.SshConnectionProperties;
import com.sshtools.j2ssh.io.IOStreamConnector;
import com.sshtools.j2ssh.session.SessionChannelClient;
import com.sshtools.j2ssh.transport.IgnoreHostKeyVerification;
import com.sshtools.j2ssh.transport.MessageStoreEOFException;
import com.sshtools.j2ssh.transport.compression.SshCompressionFactory;
import com.sshtools.j2ssh.transport.publickey.SshPrivateKey;
import com.sshtools.j2ssh.transport.publickey.SshPrivateKeyFile;

public class SSHManager {

    private static long RETRY_TIMEOUT = 60000;
    private int _retries = 6;
    
    private String _host = null;
    private String _user = null;
    private String _pass = null;
    
    private int _port = 22; // default
    
    private SshClient  _client = null;
    
    private File _privKey = null;
    
    public SSHManager(String host, String user, String pass, int port) {
        _host = host;
        _user = user;
        _pass = pass;
        _port = port;
    }

    public void setPublickey(File privKey) { 
        _privKey = privKey;
    }
    
    private synchronized SessionChannelClient connect() throws SSHException { 
        SessionChannelClient session = null;
        
        while (session == null) { 
            if (_client == null)
                _client = new SshClient();
            
            try { 
                session = _client.openSessionChannel();
            } catch (IOException e) { 
                _client = new SshClient();
            }
            
            if (!_client.isConnected())  { 
                Action.getLogger().info("Connecting to [" + _host + 
                                        ":" + _port + "]");

                int retry = 0;
                while (retry < _retries) { 
                    try { 
                        _client.connect(_host,
                                        _port,
                                        new IgnoreHostKeyVerification());
                        break;
                    } catch (IOException e) { 
                        retry++;
                        
                        Action.getLogger().info("Failed to connect to [" 
                                                + _host + ":" + _port + 
                                                ", retrying...");
                        
                        if (Action.getLogger().isDebugEnabled())
                            Action.getLogger().debug("Failed to connect, retrying...",e);
                        
                        ThreadUtil.pause(RETRY_TIMEOUT);
                    }
                }
                
                if (retry == _retries)
                    throw new SSHException("Unable to access " + _host + ":" + _port);
            }
           
            if (!_client.isAuthenticated()) { 
                            
                if (_privKey != null)  {
                    Action.getLogger().info("Private key authentication.");
                    PublicKeyAuthenticationClient auth = 
                                            new PublicKeyAuthenticationClient();
                    
                    auth = new PublicKeyAuthenticationClient();
                    try {
                        SshPrivateKeyFile keyFile = SshPrivateKeyFile.parse(_privKey);
                        SshPrivateKey key = keyFile.toPrivateKey(_pass);
                        auth.setKey(key); 
                        auth.setUsername(_user);
                        
                        auth.setAuthenticationPrompt(new SshAuthenticationPrompt() { 
                            public boolean showPrompt(SshAuthenticationClient arg0)
                                    throws AuthenticationProtocolException {
                                return false;
                            } 
                        });

                        int result = _client.authenticate(auth);

                        if (result != AuthenticationProtocolState.COMPLETE)
                            Action.getLogger().warn("Private key authentication failed.");
                                    
                    } catch (AuthenticationProtocolException e) { 
                        Action.getLogger().warn("Private key authentication failed.",e);
                    } catch (IOException e) {
                        Action.getLogger().warn("Private key authentication failed.",e);
                    }
                } 
                
                if (!_client.isAuthenticated()) {
                    PasswordAuthenticationClient auth = 
                                             new PasswordAuthenticationClient();
                    auth.setUsername(_user);
                    auth.setPassword(_pass);
                    
                    try { 
                        auth.setAuthenticationPrompt(new SshAuthenticationPrompt() { 
                            public boolean showPrompt(SshAuthenticationClient arg0)
                                    throws AuthenticationProtocolException {
                                return false;
                            } 
                        });
                        
                        int result = _client.authenticate(auth);

                        if (result != AuthenticationProtocolState.COMPLETE)
                            Action.getLogger().warn("Private key authentication failed.");
                        
                    } catch (IOException e) {
                        Action.getLogger().warn("User/pass authentication failed.",e);
                    }
                }
                
                if (!_client.isAuthenticated())    
                    throw new SSHException("Authentication failed to [" +
                                           _host + ":" +_port + "]");
            }
        }
        
        return session;
    }
    
    public SSHCommand execute(String cmd,
                              InputStream  stdin,
                              OutputStream stdout, 
                              OutputStream stderr) throws SSHException { 
       SessionChannelClient session = connect();
       Thread t =  null;
       try { 
           if (stdin != null) {
               
               /* 
                * TODO: needs aj more elegant solution with the bindInputStream 
                *       method.
                *       
                * XXX:
                * bindInputStream method on SessionChannelClient doesn't work 
                * well and has trouble pushing the bytes down the stream 
                * correctly. I'll open a bug against ssh-tools but meanwhile
                * the easiest way for this to work is to push the bytes down 
                * the OutputStream manually.
                */
               final SessionChannelClient s = session;
               final InputStream in = stdin;
               t= new Thread() {
                    public void run() {
                        OutputStream os = s.getOutputStream();

                        byte[] buff = new byte[16*1024];
                        try {
                            int read = 0;
                            while ((read = in.read(buff)) != -1) {
                                os.write(buff,0,read);
                            }
                           
                            os.flush();
                            os.close();
                        } catch (IOException e) {
                            /* 
                             * TODO: 
                             * this seems to happen during the setprops I'll 
                             * need to figure out exactly why.
                             */
                            Action.getLogger().warn("Error closing stream.",e);
                        }
                    }
                };
           }
               
           if (stdout != null) {
               // this should connect the output of the process to stdout
               session.bindOutputStream(stdout);
           }

           if (stderr != null) { 
               // this should connect the error of the process to stderr
               new IOStreamConnector(session.getStderrInputStream(), stderr);
           }
               
           try { 
               if (session.executeCommand(cmd)) { 
                   /*
                    * XXX: t can not start before the command does it will deadlock
                    *      the whole application because of acessing the 
                    *      Connectionprotocol class to write to the stream but 
                    *      no stream is actually available so things will get stuck.
                    */
                   if (t != null)
                       t.start();
                   return new SSHCommand(cmd, session);
               } else { 
                   throw new SSHException("Unable to execute command [" + cmd + 
                                          "], got return code: " + 
                                          session.getExitCode());
               }  
           } catch (MessageStoreEOFException e) { 
               /*
                * XXX: known bug when rebooting the remote machine.
                * 
                */
               Action.getLogger().warn("Caught MessageStoreEOFException bug " + 
                                       "while sshing to " + _host + ":" + _port);
               return new SSHCommand(cmd, session);
           }
       } catch (RemoteCmdException e) { 
           throw new SSHException("Unable to execute command [" + cmd + 
                                  "], got return code: " + 
                                  session.getExitCode());
       } catch (IOException e) { 
           throw new SSHException("Unable to execute command [" + cmd + 
                                  "], got return code: " + 
                                  session.getExitCode());
       }
    }
    
}
