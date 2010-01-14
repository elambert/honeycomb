/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



import java.util.*;
import java.text.*;
import java.io.*;

// Import archive-related packages
import com.sun.honeycomb.client.*;
import com.sun.honeycomb.common.*;


public class Util
{
  public static void sleep(Object o, long millis)
  {
    long t0 = System.currentTimeMillis();
    while (System.currentTimeMillis() - t0 < millis)
    {
      synchronized (o)
      {
        try
        {
          o.wait(millis);
        }
        catch (java.lang.Throwable t)
        {
        }
      }
    }
  }

  public static String fit(String s, int length)
  {
    return Util.fit(s, length, false);
  }

  public static String fit(String s, int length, boolean prepend)
  {
    String fitted;

    if (s.length() > length)
    {
      fitted = s.substring(0, length);
    }
    else
    {
      StringBuffer sb = new StringBuffer();
      if (!prepend)
      {
        sb.append(s);
      }
      for (int i = 0; s.length() + i < length; i++)
      {
        sb.append(" ");
      } 
      if (prepend)
      {
        sb.append(s);
      }
      fitted = sb.toString();
    }

    return fitted;
  }

  public static String nowString()
  {
    return DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG).format(new Date());
  }

  public static void printMap(Map m)
  {
    Iterator keys = m.keySet().iterator();
    while (keys.hasNext())
    {
      Object next = keys.next();
      System.out.print(next.toString());
      System.out.print(" => ");
      System.out.println(m.get(next).toString());
    }
  }


    public static NameValueObjectArchive 
        getNameValueObjectArchive(String server) 
        throws ArchiveException, IOException  {

        String [] res = null;
        int port = -1;

        res = server.split(":");
        if (res.length > 1) {
            server = res[0];
            port = Integer.parseInt(res[1]);
        }

        if (port == -1) {
            return new NameValueObjectArchive(server);
        } else {
            return new NameValueObjectArchive(server, port);
        }
    }

  public static boolean ping(NameValueObjectArchive archive)
  {
    try
    {
      archive.getSchema();
      return true;
    }
    catch (Throwable t)
    {
        return false;
    }
  }

}
