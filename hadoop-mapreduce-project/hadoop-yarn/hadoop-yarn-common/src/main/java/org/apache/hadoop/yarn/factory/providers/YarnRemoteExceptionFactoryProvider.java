/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.yarn.factory.providers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.YarnException;
import org.apache.hadoop.yarn.factories.YarnRemoteExceptionFactory;
import org.apache.hadoop.yarn.factories.impl.pb.YarnRemoteExceptionFactoryPBImpl;

public class YarnRemoteExceptionFactoryProvider {

  public static final String RPC_SERIALIZER_KEY = "org.apache.yarn.ipc.rpc.serializer.property";
  public static final String RPC_SERIALIZER_DEFAULT = "protocolbuffers";
  
  public static final String EXCEPTION_FACTORY_CLASS_KEY = "org.apache.yarn.ipc.exception.factory.class";
  
  private YarnRemoteExceptionFactoryProvider() {
  }
  
  public static YarnRemoteExceptionFactory getYarnRemoteExceptionFactory(Configuration conf) {
    if (conf == null) {
      conf = new Configuration();
    }
    String recordFactoryClassName = conf.get(EXCEPTION_FACTORY_CLASS_KEY);
    if (recordFactoryClassName == null) {
      String serializer = conf.get(RPC_SERIALIZER_KEY, RPC_SERIALIZER_DEFAULT);
      if (serializer.equals(RPC_SERIALIZER_DEFAULT)) {
        return YarnRemoteExceptionFactoryPBImpl.get();
      } else {
        throw new YarnException("Unknown serializer: [" + conf.get(RPC_SERIALIZER_KEY) + "]. Use keys: [" + EXCEPTION_FACTORY_CLASS_KEY + "] to specify Exception factory");
      }
    } else {
      return (YarnRemoteExceptionFactory) getFactoryClassInstance(recordFactoryClassName);
    }
  }
  
  private static Object getFactoryClassInstance(String factoryClassName) {
    try {
      Class clazz = Class.forName(factoryClassName);
      Method method = clazz.getMethod("get", null);
      method.setAccessible(true);
      return method.invoke(null, null);
    } catch (ClassNotFoundException e) {
      throw new YarnException(e);
    } catch (NoSuchMethodException e) {
      throw new YarnException(e);
    } catch (InvocationTargetException e) {
      throw new YarnException(e);
    } catch (IllegalAccessException e) {
      throw new YarnException(e);
    }
  }
}
