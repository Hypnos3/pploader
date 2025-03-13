/*
Copyright 2014 Lisovik Denisckyberlis@gmail.com
This file is part of PPLoader.
PPLoader is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

PPLoader is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with PPLoader.  If not, see <http://www.gnu.org/licenses/>
*/
package org.cyberlis.pyloader;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.apache.commons.lang3.Validate;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.TimedRegisteredListener;
import org.bukkit.plugin.UnknownDependencyException;
import org.cyberlis.dataloaders.PluginDataFile;
import org.cyberlis.dataloaders.PluginPythonDirectory;
import org.cyberlis.dataloaders.PluginPythonZip;
import org.cyberlis.plugin.BukkitPluginManagerWrapper;
import org.cyberlis.plugin.PaperPluginManagerWrapper;
import org.cyberlis.plugin.PluginManagerWrapper;
import org.cyberlis.pyloader.PythonPlugin;
import org.python.core.PyDictionary;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PySystemState;
import org.python.jline.internal.Log;
import org.python.util.PythonInterpreter;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * A jython plugin loader. depends on JavaPluginLoader and SimplePluginManager.
 */
public class PythonPluginLoader implements PluginLoader {

    private final Server server;
    private PluginManagerWrapper pmw;

    /**
     * Filter - matches all of the following, for the regex illiterate:
     * <pre>
     * plugin_py_dir
     * plugin.py.dir
     * plugin.py.zip
     * plugin.pyp
     * </pre>
     */
    public static final Pattern[] fileFilters = new Pattern[] {
            Pattern.compile("^(.*)\\.py\\.dir$"),
            Pattern.compile("^(.*)_py_dir$"),
            Pattern.compile("^(.*)\\.py\\.zip$"),
            Pattern.compile("^(.*)\\.pyp$"),
        };

    private HashSet<String> loadedplugins = new HashSet<String>();

    /**
     * @param server server to initialize with
     */
    public PythonPluginLoader(Server server) {
        this.server = server;
    }

    public Plugin loadPlugin(File file) throws InvalidPluginException/*, UnknownDependencyException*/ {
        return loadPlugin(file, false);
    }

    public Plugin loadPlugin(File file, boolean ignoreSoftDependencies)
            throws InvalidPluginException/*, InvalidDescriptionException, UnknownDependencyException*/ {
        if (!file.exists()) {
            throw new InvalidPluginException(new FileNotFoundException(String.format("%s does not exist",
                    file.getPath())));
        }

        PluginDataFile data = null;

        if (file.getName().endsWith(".dir") || file.getName().endsWith("_dir")) {
            if (!file.isDirectory()) {
                throw new InvalidPluginException(
                        new Exception("python directories cannot be normal files! try .py or .py.zip instead."));
            }
            data = new PluginPythonDirectory(file);
        } else if (file.getName().endsWith(".zip") || file.getName().endsWith(".pyp")) {
            if (file.isDirectory()) {
                throw new InvalidPluginException(
                        new Exception("python zips cannot be directories! try .py.dir instead."));
            }
            data = new PluginPythonZip(file);
        } else {
            throw new InvalidPluginException(new Exception("filename '"+file.getName()+"' does not end in py, dir, zip, or pyp! did you add a regex without altering loadPlugin()?"));
        }

        try {
            return loadPlugin(file, ignoreSoftDependencies, data);
        } finally {
            try {
                data.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Properties setDefaultPythonPath(Properties props, String file_path) {
        String pythonPathProp = props.getProperty("python.path");
        String new_value;
        if (pythonPathProp==null) {
            new_value  = file_path;
        } else {
            new_value = pythonPathProp +java.io.File.pathSeparator + file_path + java.io.File.pathSeparator;
        }
        props.setProperty("python.path",new_value);
        return props;
    }

    private Plugin loadPlugin(File file, boolean ignoreSoftDependencies, PluginDataFile data)
            throws InvalidPluginException/*, InvalidDescriptionException, UnknownDependencyException*/ {
        Properties props;
        // System.out.println("[PPLoader] Loading Plugin " + file.getName());
        server.getLogger().info("[PPLoader] Loading Plugin " + file.getName());
        PythonPlugin result = null;
        PluginDescriptionFile description = null;
        InputStream stream = null;
        try {
            stream = data.getStream("plugin.yml");
            if (stream == null) {
                throw new InvalidPluginException(new Exception("You must include plugin.yml!"));
            }
            description = new PluginDescriptionFile(stream);
        } catch (IOException ex) {
            throw new InvalidPluginException(ex);
        } catch (YAMLException ex) {
            throw new InvalidPluginException(ex);
        } catch (InvalidDescriptionException ex) {
            throw new InvalidPluginException(ex);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    throw new InvalidPluginException(e);
                }
            }
        }

        File dataFolder = new File(file.getParentFile(), description.getName());

        if (dataFolder.getAbsolutePath().equals(file.getAbsolutePath())) {
            throw new InvalidPluginException(new Exception(String.format("Projected datafolder: '%s' for %s is the same file as the plugin itself (%s)",
                    dataFolder,
                    description.getName(),
                    file)));
        }

        if (dataFolder.exists() && !dataFolder.isDirectory()) {
            throw new InvalidPluginException(new Exception(String.format("Projected datafolder: '%s' for %s (%s) exists and is not a directory",
                    dataFolder,
                    description.getName(),
                    file)));
        }

        List<String> depend;

        try {
            depend = description.getDepend();
            if (depend == null) {
                depend = new ArrayList<>();
            }
        } catch (ClassCastException ex) {
            throw new InvalidPluginException(ex);
        }

        for (String pluginName : depend) {
            if (!isPluginLoaded(pluginName)) {
                throw new UnknownDependencyException(pluginName);
            }
        }
        props = PySystemState.getBaseProperties();
        props = setDefaultPythonPath(props, file.getAbsolutePath());

        PySystemState state = new PySystemState();
        PySystemState.initialize(System.getProperties(), props, null);
        PyList pythonpath = state.path;
        PyString filepath = new PyString(file.getAbsolutePath());
        pythonpath.append(filepath);


        String mainfile = "plugin.py";
        InputStream instream = null;
        try {
            instream = data.getStream(mainfile);
            if (instream == null) {
                mainfile = "main.py";
                instream = data.getStream(mainfile);
            }
        } catch (IOException e) {
            throw new InvalidPluginException(e);
        }

        if (instream == null) {
            throw new InvalidPluginException(new FileNotFoundException("Can not find plugin.py or main.py"));
        }
        PythonInterpreter interp = null;
        try {
            PyDictionary table = new PyDictionary();
            interp = new PythonInterpreter(table, state);

            String[] pre_plugin_scripts = {"preload.py"};
            String[] post_plugin_scripts = {"postload.py"};

            // Run scripts designed to be run before plugin creation
            for (String script : pre_plugin_scripts) {
                InputStream metastream = null;
                try {
                    metastream = this.getClass().getClassLoader().getResourceAsStream("scripts/"+script);
                    if (metastream != null) {
                        interp.execfile(metastream);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    server.getLogger().severe("Exception while executing \"" + script + "\": " + ex.getMessage());
                } finally {
                    if (metastream != null) {
                        metastream.close();
                    }
                }
            }

            String imports = "from org.cyberlis.pyloader import PythonPlugin\n";
            instream = new SequenceInputStream(Collections.enumeration(Arrays.asList(
                new ByteArrayInputStream(imports.getBytes()),
                instream)));
      
            interp.execfile(instream);

            instream.close();

            String mainclass = description.getMain();
            PyObject pyClass = interp.get(mainclass);
            if (pyClass == null) {
                pyClass = interp.get("Plugin");
                if (pyClass == null) {
                    throw new InvalidPluginException(new Exception("Can not find Mainclass."));
                }
            }
            result = (org.cyberlis.pyloader.PythonPlugin) pyClass.__call__().__tojava__(org.cyberlis.pyloader.PythonPlugin.class);

            interp.set("PYPLUGIN", result);

            result.interp = interp;

            // Run scripts designed to be run after plugin creation
            for (String script : post_plugin_scripts) {
                InputStream metastream = null;
                try {
                    metastream = this.getClass().getClassLoader().getResourceAsStream("scripts/"+script);
                    if (metastream != null) {
                        interp.execfile(metastream);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    server.getLogger().severe("Exception while executing \"" + script + "\": " + ex.getMessage());
                } finally {
                    if (metastream != null) {
                        metastream.close();
                    }
                }
            }

            result.initialize(this, server, description, dataFolder, file);
            result.setDataFile(data);

        } catch (Throwable t) {
            throw new InvalidPluginException(t);
        } finally {
            if (interp != null) {
                try {
                    interp.close();
                } catch (Throwable t) {
                    throw new InvalidPluginException(t);
                }
            }
        }

        if (!loadedplugins.contains(description.getName())) {
            loadedplugins.add(description.getName());
        }
        return result;
    }

    private boolean isPluginLoaded(String name) {
        if (loadedplugins.contains(name)) {
            return true;
        }
        if (getPlugInManager().isJavaPluginLoaded(name)) {
            return true;
        }
        return false;
    }

    public Pattern[] getPluginFileFilters() {
        return fileFilters;
    }

    public void disablePlugin(Plugin plugin) {
        if (!(plugin instanceof PythonPlugin)) {
            throw new IllegalArgumentException("Plugin is not associated with this PluginLoader");
        }

        if (plugin.isEnabled()) {
            PythonPlugin pyPlugin = (PythonPlugin) plugin;

            try {
                pyPlugin.setEnabled(false);
            } catch (Throwable ex) {
                server.getLogger().log(Level.SEVERE,
                        "Error occurred while disabling " + plugin.getDescription().getFullName()
                                + " (Is it up to date?): " + ex.getMessage(),
                        ex);
            }

            getPlugInManager().callEvent(new PluginDisableEvent(plugin));

            String pluginName = pyPlugin.getDescription().getName();
            if (loadedplugins.contains(pluginName)) {
                loadedplugins.remove(pluginName);
            }
        }
    }

    public void enablePlugin(Plugin plugin) {
        if (!(plugin instanceof PythonPlugin)) {
            throw new IllegalArgumentException("Plugin is not associated with this PluginLoader");
        }

        if (!plugin.isEnabled()) {
            PythonPlugin pyPlugin = (PythonPlugin) plugin;

            String pluginName = pyPlugin.getDescription().getName();
            server.getLogger().info("enable PhytonPlugin " + pluginName);

            if (!loadedplugins.contains(pluginName)) {
                loadedplugins.add(pluginName);
            }

            try {
                pyPlugin.setEnabled(true);
            } catch (Throwable ex) {
                server.getLogger().log(Level.SEVERE,
                        "Error occurred while enabling " + plugin.getDescription().getFullName()
                                + " (Is it up to date?): " + ex.getMessage(),
                        ex);
            }

            // Perhaps abort here, rather than continue going, but as it stands,
            // an abort is not possible the way it's currently written
            getPlugInManager().callEvent(new PluginEnableEvent(plugin));
        }
    }

    @Override
    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(
            Listener listener, Plugin plugin) {
        boolean useTimings = getPlugInManager().useTimings();
        Map<Class<? extends Event>, Set<RegisteredListener>> ret = new HashMap<Class<? extends Event>, Set<RegisteredListener>>();
        PythonListener pyListener = (PythonListener)listener;

        for(Map.Entry<Class<? extends Event>, Set<PythonEventHandler>> entry : pyListener.handlers.entrySet()) {
            Set<RegisteredListener> eventSet = new HashSet<RegisteredListener>();

            for(final PythonEventHandler handler : entry.getValue()) {
                EventExecutor executor = new EventExecutor() {

                    @Override
                    public void execute(Listener listener, Event event) throws EventException {
                        ((PythonListener)listener).fireEvent(event, handler);
                    }
                };
                if(useTimings) {
                    eventSet.add(new TimedRegisteredListener(pyListener, executor, handler.priority, plugin, false));
                } else {
                    eventSet.add(new RegisteredListener(pyListener, executor, handler.priority, plugin, false));
                }
            }
            ret.put(entry.getKey(), eventSet);
        }
        return ret;
    }

    @Override
    public PluginDescriptionFile getPluginDescription(File file)
            throws InvalidDescriptionException {
        Validate.notNull(file, "File cannot be null");

        InputStream stream = null;
        PluginDataFile data = null;

        if (file.getName().endsWith(".dir") || file.getName().endsWith("_dir")) {
            if (!file.isDirectory()) {
                throw new InvalidDescriptionException(new InvalidPluginException(
                        new Exception("python directories cannot be normal files! .pyp or .py.zip instead.")));
            }
            data = new PluginPythonDirectory(file);
        } else if (file.getName().endsWith(".zip") || file.getName().endsWith(".pyp")) {
            if (file.isDirectory()) {
                throw new InvalidDescriptionException(new InvalidPluginException(
                        new Exception("python zips cannot be directories! try .py.dir instead.")));
            }
            try {
                data = new PluginPythonZip(file);
            } catch (InvalidPluginException ex) {
                throw new InvalidDescriptionException(ex);
            }
        } else {
            throw new InvalidDescriptionException(new InvalidPluginException(new Exception("filename '"+file.getName()+"' does not end in dir, zip, or pyp! did you add a regex without altering loadPlugin()?")));
        }

        try {
            stream = data.getStream("plugin.yml");

            if(stream == null) {
                //TODO Does this cause serious problems with plugins which have no plugin.yml file?
                throw new InvalidDescriptionException(new InvalidPluginException(new FileNotFoundException("Plugin does not contain plugin.yml")));
            }

            return new PluginDescriptionFile(stream);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {}
            }
        }
        return null;
    }

    private PluginManagerWrapper getPlugInManager() {
        if (pmw == null) {
            try {
                Class.forName("io.papermc.paper.plugin.manager.PaperPluginManagerImpl");
                pmw = new PaperPluginManagerWrapper();
                Log.info("Paper detected, using PaperPluginManagerImpl");
            } catch (ClassNotFoundException e) {
                pmw = new BukkitPluginManagerWrapper();
                Log.info("Paper not detected, using BukkitPluginManager");
            }
        }
        return pmw;
    }
}
