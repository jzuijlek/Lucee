/**
 * Copyright (c) 2014, the Railo Company Ltd.
 * Copyright (c) 2015, Lucee Assosication Switzerland
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package lucee.runtime.functions.system;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import lucee.commons.date.TimeZoneUtil;
import lucee.commons.io.res.Resource;
import lucee.commons.lang.StringUtil;
import lucee.commons.lang.SystemOut;
import lucee.runtime.Component;
import lucee.runtime.ComponentSpecificAccess;
import lucee.runtime.Mapping;
import lucee.runtime.PageContext;
import lucee.runtime.PageContextImpl;
import lucee.runtime.cache.CacheConnection;
import lucee.runtime.config.Config;
import lucee.runtime.config.ConfigWebUtil;
import lucee.runtime.db.ClassDefinition;
import lucee.runtime.db.DataSource;
import lucee.runtime.exp.ApplicationException;
import lucee.runtime.exp.PageException;
import lucee.runtime.i18n.LocaleFactory;
import lucee.runtime.listener.AppListenerUtil;
import lucee.runtime.listener.ApplicationContext;
import lucee.runtime.listener.ApplicationContextSupport;
import lucee.runtime.listener.JavaSettings;
import lucee.runtime.listener.ModernApplicationContext;
import lucee.runtime.net.mail.Server;
import lucee.runtime.net.mail.ServerImpl;
import lucee.runtime.net.s3.Properties;
import lucee.runtime.op.Caster;
import lucee.runtime.orm.ORMConfiguration;
import lucee.runtime.type.Array;
import lucee.runtime.type.ArrayImpl;
import lucee.runtime.type.Collection;
import lucee.runtime.type.Collection.Key;
import lucee.runtime.type.KeyImpl;
import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;
import lucee.runtime.type.UDF;
import lucee.runtime.type.dt.TimeSpanImpl;
import lucee.runtime.type.scope.Scope;
import lucee.runtime.type.scope.Undefined;
import lucee.runtime.type.util.ArrayUtil;
import lucee.runtime.type.util.KeyConstants;
import lucee.runtime.type.util.ListUtil;

public class GetApplicationSettings {

	public static Struct call(PageContext pc) {
		return call(pc, false);
	}

	public static Struct call(PageContext pc, boolean suppressFunctions) {

		ApplicationContext appContext = pc.getApplicationContext();
		Component cfc = null;
		if(appContext instanceof ModernApplicationContext)
			cfc = ((ModernApplicationContext)appContext).getComponent();

		Struct sct = new StructImpl(Struct.TYPE_LINKED);
		sct.setEL("applicationTimeout", appContext.getApplicationTimeout());
		sct.setEL("clientManagement", Caster.toBoolean(appContext.isSetClientManagement()));
		sct.setEL("clientStorage", appContext.getClientstorage());
		sct.setEL("sessionStorage", appContext.getSessionstorage());
		sct.setEL("customTagPaths", toArray(appContext.getCustomTagMappings()));
		sct.setEL("componentPaths", toArray(appContext.getComponentMappings()));
		sct.setEL("loginStorage", AppListenerUtil.translateLoginStorage(appContext.getLoginStorage()));
		sct.setEL(KeyConstants._mappings, toStruct(appContext.getMappings()));
		sct.setEL(KeyConstants._name, appContext.getName());
		sct.setEL("scriptProtect", AppListenerUtil.translateScriptProtect(appContext.getScriptProtect()));
		sct.setEL("secureJson", Caster.toBoolean(appContext.getSecureJson()));
		sct.setEL("CGIReadOnly", Caster.toBoolean(appContext.getCGIScopeReadonly()));
		sct.setEL("typeChecking", Caster.toBoolean(appContext.getTypeChecking()));
		sct.setEL("secureJsonPrefix", appContext.getSecureJsonPrefix());
		sct.setEL("sessionManagement", Caster.toBoolean(appContext.isSetSessionManagement()));
		sct.setEL("sessionTimeout", appContext.getSessionTimeout());
		sct.setEL("clientTimeout", appContext.getClientTimeout());
		sct.setEL("setClientCookies", Caster.toBoolean(appContext.isSetClientCookies()));
		sct.setEL("setDomainCookies", Caster.toBoolean(appContext.isSetDomainCookies()));
		sct.setEL(KeyConstants._name, appContext.getName());
		sct.setEL("localMode", appContext.getLocalMode() == Undefined.MODE_LOCAL_OR_ARGUMENTS_ALWAYS ? Boolean.TRUE : Boolean.FALSE);
		sct.setEL(KeyConstants._locale, LocaleFactory.toString(pc.getLocale()));
		sct.setEL(KeyConstants._timezone, TimeZoneUtil.toString(pc.getTimeZone()));

		// scope cascading
		sct.setEL("scopeCascading", ConfigWebUtil.toScopeCascading(appContext.getScopeCascading(), null));

		if(appContext.getScopeCascading() != Config.SCOPE_SMALL) {
			sct.setEL("searchImplicitScopes", appContext.getScopeCascading() == Config.SCOPE_STANDARD);
		}

		Struct cs = new StructImpl(Struct.TYPE_LINKED);
		cs.setEL("web", pc.getWebCharset().name());
		cs.setEL("resource", ((PageContextImpl)pc).getResourceCharset().name());
		sct.setEL("charset", cs);

		sct.setEL("sessionType", AppListenerUtil.toSessionType(((PageContextImpl)pc).getSessionType(), "application"));
		sct.setEL("serverSideFormValidation", Boolean.FALSE); // TODO impl

		sct.setEL("clientCluster", Caster.toBoolean(appContext.getClientCluster()));
		sct.setEL("sessionCluster", Caster.toBoolean(appContext.getSessionCluster()));

		sct.setEL("invokeImplicitAccessor", Caster.toBoolean(appContext.getTriggerComponentDataMember()));
		sct.setEL("triggerDataMember", Caster.toBoolean(appContext.getTriggerComponentDataMember()));
		sct.setEL("sameformfieldsasarray", Caster.toBoolean(appContext.getSameFieldAsArray(Scope.SCOPE_FORM)));
		sct.setEL("sameurlfieldsasarray", Caster.toBoolean(appContext.getSameFieldAsArray(Scope.SCOPE_URL)));

		Object ds = appContext.getDefDataSource();
		if(ds instanceof DataSource)
			ds = _call((DataSource)ds);
		else
			ds = Caster.toString(ds, null);
		sct.setEL(KeyConstants._datasource, ds);
		sct.setEL("defaultDatasource", ds);

		Resource src = appContext.getSource();
		if(src != null)
			sct.setEL(KeyConstants._source, src.getAbsolutePath());

		// orm
		if(appContext.isORMEnabled()) {
			ORMConfiguration conf = appContext.getORMConfiguration();
			if(conf != null)
				sct.setEL(KeyConstants._orm, conf.toStruct());
		}
		// s3
		Properties props = appContext.getS3();
		if(props != null) {
			sct.setEL(KeyConstants._s3, props.toStruct());
		}

		// ws settings
		{
			Struct wssettings = new StructImpl(Struct.TYPE_LINKED);
			wssettings.put(KeyConstants._type, AppListenerUtil.toWSType(appContext.getWSType(), "Axis1"));
			sct.setEL("wssettings", wssettings);
		}

		// datasources
		Struct _sources = new StructImpl(Struct.TYPE_LINKED);
		sct.setEL(KeyConstants._datasources, _sources);
		DataSource[] sources = appContext.getDataSources();
		if(!ArrayUtil.isEmpty(sources)) {
			for (int i = 0; i < sources.length; i++) {
				_sources.setEL(KeyImpl.init(sources[i].getName()), _call(sources[i]));
			}

		}

		// logs
		Struct _logs = new StructImpl(Struct.TYPE_LINKED);
		sct.setEL("logs", _logs);
		if(appContext instanceof ApplicationContextSupport) {
			ApplicationContextSupport acs = (ApplicationContextSupport)appContext;
			Iterator<Key> it = acs.getLogNames().iterator();
			Key name;
			while(it.hasNext()) {
				name = it.next();
				_logs.setEL(name, acs.getLogMetaData(name.getString()));
			}
		}

		// mails
		Array _mails = new ArrayImpl();
		sct.setEL("mails", _mails);
		if(appContext instanceof ApplicationContextSupport) {
			ApplicationContextSupport acs = (ApplicationContextSupport)appContext;
			Server[] servers = acs.getMailServers();
			Struct s;
			Server srv;
			if(servers != null) {
				for (int i = 0; i < servers.length; i++) {
					srv = servers[i];
					s = new StructImpl(Struct.TYPE_LINKED);
					_mails.appendEL(s);
					s.setEL(KeyConstants._host, srv.getHostName());
					s.setEL(KeyConstants._port, srv.getPort());
					if(!StringUtil.isEmpty(srv.getUsername()))
						s.setEL(KeyConstants._username, srv.getUsername());
					if(!StringUtil.isEmpty(srv.getPassword()))
						s.setEL(KeyConstants._password, srv.getPassword());
					s.setEL(KeyConstants._readonly, srv.isReadOnly());
					s.setEL("ssl", srv.isSSL());
					s.setEL("tls", srv.isTLS());

					if(srv instanceof ServerImpl) {
						ServerImpl srvi = (ServerImpl)srv;
						s.setEL("lifeTimespan", TimeSpanImpl.fromMillis(srvi.getLifeTimeSpan()));
						s.setEL("idleTimespan", TimeSpanImpl.fromMillis(srvi.getIdleTimeSpan()));
					}
				}
			}
		}

		// serialization
		Struct serialization = new StructImpl(Struct.TYPE_LINKED);
		sct.setEL("serialization", serialization);
		if (appContext instanceof ModernApplicationContext){
			ModernApplicationContext mAppContext = (ModernApplicationContext)appContext;
			Struct settings = mAppContext.getSerializationSettings();
			if (settings != null)
				serialization.putAll(settings);
		}

		// tag
		Map<Key, Map<Collection.Key, Object>> tags = appContext.getTagAttributeDefaultValues(pc);
		if(tags != null) {
			Struct tag = new StructImpl(Struct.TYPE_LINKED);
			Iterator<Entry<Key, Map<Collection.Key, Object>>> it = tags.entrySet().iterator();
			Entry<Collection.Key, Map<Collection.Key, Object>> e;
			Iterator<Entry<Collection.Key, Object>> iit;
			Entry<Collection.Key, Object> ee;
			Struct tmp;
			// TagLib lib = ((ConfigImpl)pc.getConfig()).getCoreTagLib();
			while(it.hasNext()) {
				e = it.next();
				iit = e.getValue().entrySet().iterator();
				tmp = new StructImpl(Struct.TYPE_LINKED);
				while(iit.hasNext()) {
					ee = iit.next();
					// lib.getTagByClassName(ee.getKey());
					tmp.setEL(ee.getKey(), ee.getValue());
				}
				tag.setEL(e.getKey(), tmp);

			}
			sct.setEL(KeyConstants._tag, tag);
		}

		// cache
		String fun = appContext.getDefaultCacheName(Config.CACHE_TYPE_FUNCTION);
		String obj = appContext.getDefaultCacheName(Config.CACHE_TYPE_OBJECT);
		String qry = appContext.getDefaultCacheName(Config.CACHE_TYPE_QUERY);
		String res = appContext.getDefaultCacheName(Config.CACHE_TYPE_RESOURCE);
		String tmp = appContext.getDefaultCacheName(Config.CACHE_TYPE_TEMPLATE);
		String inc = appContext.getDefaultCacheName(Config.CACHE_TYPE_INCLUDE);
		String htt = appContext.getDefaultCacheName(Config.CACHE_TYPE_HTTP);
		String fil = appContext.getDefaultCacheName(Config.CACHE_TYPE_FILE);
		String wse = appContext.getDefaultCacheName(Config.CACHE_TYPE_WEBSERVICE);

		// cache connections
		Struct conns = new StructImpl(Struct.TYPE_LINKED);
		if(appContext instanceof ApplicationContextSupport) {
			ApplicationContextSupport acs = (ApplicationContextSupport)appContext;
			Key[] names = acs.getCacheConnectionNames();
			for (Key name : names) {
				CacheConnection data = acs.getCacheConnection(name.getString(), null);
				Struct _sct = new StructImpl(Struct.TYPE_LINKED);
				conns.setEL(name, _sct);
				_sct.setEL(KeyConstants._custom, data.getCustom());
				_sct.setEL(KeyConstants._storage, data.isStorage());
				ClassDefinition cd = data.getClassDefinition();
				if(cd != null) {
					_sct.setEL(KeyConstants._class, cd.getClassName());
					if(!StringUtil.isEmpty(cd.getName()))
						_sct.setEL(KeyConstants._bundleName, cd.getClassName());
					if(cd.getVersion() != null)
						_sct.setEL(KeyConstants._bundleVersion, cd.getVersionAsString());
				}
			}
		}

		if(!conns.isEmpty() || fun != null || obj != null || qry != null || res != null || tmp != null || inc != null || htt != null || fil != null
				|| wse != null) {
			Struct cache = new StructImpl(Struct.TYPE_LINKED);
			sct.setEL(KeyConstants._cache, cache);
			if(fun != null)
				cache.setEL(KeyConstants._function, fun);
			if(obj != null)
				cache.setEL(KeyConstants._object, obj);
			if(qry != null)
				cache.setEL(KeyConstants._query, qry);
			if(res != null)
				cache.setEL(KeyConstants._resource, res);
			if(tmp != null)
				cache.setEL(KeyConstants._template, tmp);
			if(inc != null)
				cache.setEL(KeyConstants._include, inc);
			if(htt != null)
				cache.setEL(KeyConstants._http, htt);
			if(fil != null)
				cache.setEL(KeyConstants._file, fil);
			if(wse != null)
				cache.setEL(KeyConstants._webservice, wse);
			if(conns != null)
				cache.setEL(KeyConstants._connections, conns);
		}

		// java settings
		JavaSettings js = appContext.getJavaSettings();
		StructImpl jsSct = new StructImpl(Struct.TYPE_LINKED);
		jsSct.put("loadCFMLClassPath", js.loadCFMLClassPath());
		jsSct.put("reloadOnChange", js.reloadOnChange());
		jsSct.put("watchInterval", new Double(js.watchInterval()));
		jsSct.put("watchExtensions", ListUtil.arrayToList(js.watchedExtensions(), ","));
		Resource[] reses = js.getResources();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < reses.length; i++) {
			if(i > 0)
				sb.append(',');
			sb.append(reses[i].getAbsolutePath());
		}
		jsSct.put("loadCFMLClassPath", sb.toString());
		sct.put("javaSettings", jsSct);
		// REST Settings
		// MUST

		if(cfc != null) {
			sct.setEL(KeyConstants._component, cfc.getPageSource().getDisplayPath());

			try {
				ComponentSpecificAccess cw = ComponentSpecificAccess.toComponentSpecificAccess(Component.ACCESS_PRIVATE, cfc);
				Iterator<Key> it = cw.keyIterator();
				Collection.Key key;
				Object value;
				while(it.hasNext()) {
					key = it.next();
					value = cw.get(key);
					if(suppressFunctions && value instanceof UDF)
						continue;
					if(!sct.containsKey(key))
						sct.setEL(key, value);
				}
			}
			catch (PageException e) {
				SystemOut.printDate(e);
			}
		}
		return sct;
	}

	private static Struct _call(DataSource source) {
		Struct s = new StructImpl(Struct.TYPE_LINKED);
		s.setEL(KeyConstants._class, source.getClassDefinition().getClassName());
		s.setEL(KeyConstants._bundleName, source.getClassDefinition().getName());
		s.setEL(KeyConstants._bundleVersion, source.getClassDefinition().getVersionAsString());

		if(source.getConnectionLimit() >= 0)
			s.setEL(AppListenerUtil.CONNECTION_LIMIT, Caster.toDouble(source.getConnectionLimit()));
		if(source.getConnectionTimeout() != 1)
			s.setEL(AppListenerUtil.CONNECTION_TIMEOUT, Caster.toDouble(source.getConnectionTimeout()));
		s.setEL(AppListenerUtil.CONNECTION_STRING, source.getDsnTranslated());
		if(source.getMetaCacheTimeout() != 60000)
			s.setEL(AppListenerUtil.META_CACHE_TIMEOUT, Caster.toDouble(source.getMetaCacheTimeout()));
		s.setEL(KeyConstants._username, source.getUsername());
		s.setEL(KeyConstants._password, source.getPassword());
		if(source.getTimeZone() != null)
			s.setEL(AppListenerUtil.TIMEZONE, source.getTimeZone().getID());
		if(source.isBlob())
			s.setEL(AppListenerUtil.BLOB, source.isBlob());
		if(source.isClob())
			s.setEL(AppListenerUtil.CLOB, source.isClob());
		if(source.isReadOnly())
			s.setEL(AppListenerUtil.READ_ONLY, source.isReadOnly());
		if(source.isStorage())
			s.setEL(AppListenerUtil.STORAGE, source.isStorage());
		return s;
	}

	private static Array toArray(Mapping[] mappings) {
		Array arr = new ArrayImpl();
		if(mappings != null) {
			String str;
			Struct sct;
			Mapping m;
			for (int i = 0; i < mappings.length; i++) {
				m = mappings[i];
				sct = new StructImpl();
				// physical
				str = m.getStrPhysical();
				if(!StringUtil.isEmpty(str, true))
					sct.setEL("primary", str.trim());
				// archive
				str = m.getStrArchive();
				if(!StringUtil.isEmpty(str, true))
					sct.setEL("archive", str.trim());
				// primary
				sct.setEL("primary", m.isPhysicalFirst() ? "physical" : "archive");

				arr.appendEL(sct);
			}
		}
		return arr;
	}

	private static Struct toStruct(Mapping[] mappings) {
		Struct sct = new StructImpl(Struct.TYPE_LINKED);
		if(mappings != null)
			for (int i = 0; i < mappings.length; i++) {
				sct.setEL(KeyImpl.init(mappings[i].getVirtual()), mappings[i].getStrPhysical());
			}
		return sct;
	}
}