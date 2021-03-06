package info.gomeow.chester;

import info.gomeow.chester.API.AsyncChesterLogEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jibble.jmegahal.JMegaHal;

public class Chester extends JavaPlugin implements Listener {
    DataStore ds;

    public static String LINK;
    public static boolean UPDATE;
    public static String NEWVERSION;

    List<String> triggerwords;

    List<String> newSentences = new ArrayList<String>();

    ChesterCommunicator chester;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        if(getConfig().getString("chatcolor") == null) {
            getConfig().set("chatcolor", "r");
        }
        if(getConfig().getString("check-update") == null) {
            getConfig().set("check-update", true);
        }
        triggerwords = getConfig().getStringList("triggerwords");
        if(triggerwords.size() == 0) {
            triggerwords.add("chester");
            getLogger().info("No triggerwords found. Using chester as triggerword.");
            getLogger().info("Make sure the config.yml contains the 'triggerwords', and not just a 'triggerword'");
        }
        getLogger().info("Triggerwords: " + triggerwords);
        startChester();
        GriefPrevention gp = (GriefPrevention)getServer().getPluginManager().getPlugin("GriefPrevention");
        ds = gp.dataStore;
    }

    @Override
    public void onDisable() {
        this.chester.stop();
        this.writeNewSentences();
    }

    public void firstRun(JMegaHal hal, File f) {
        try {
            f.createNewFile();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
        hal.add("Hello World");
        hal.add("Can I have some coffee?");
    }

    public JMegaHal transfer(ObjectInputStream in) throws ClassNotFoundException, IOException {
        JMegaHal hal = (JMegaHal) in.readObject();
        if(in != null) {
            in.close();
        }
        return hal;
    }

    public void startChester() {
        try {
            File chesterFile = new File(this.getDataFolder(), "brain.chester");
            File dir = new File("plugins" + File.separator + "Chester");
            if(!dir.exists()) {
                dir.mkdirs();
            }
            File old = new File(this.getDataFolder(), "chester.brain");
            JMegaHal hal;
            if(old.exists()) {
                hal = transfer(new ObjectInputStream(new FileInputStream(old)));
            } else {
                hal = new JMegaHal();
            }
            if(chesterFile.exists()) {
                FileReader fr = new FileReader(chesterFile);
                BufferedReader br = new BufferedReader(fr);
                String line = null;
                while((line = br.readLine()) != null) {
                    hal.add(line);
                }
                br.close();
            } else {
                firstRun(hal, chesterFile);
            }
            this.chester = new ChesterCommunicator(this, hal, triggerwords);
        } catch(IOException ioe) {
        } catch(ClassNotFoundException cnfe) {
        }
    }

    public String clean(String string) {
        if(string != null && string.length() > 300) {
            string = string.substring(0, 300);
        }
        String newstring = string.replaceAll("<.*?>", "").replaceAll("\\[.*?\\]", "");
        return newstring;
    }

    public void writeNewSentences() {
        File chesterFile = new File(this.getDataFolder(), "brain.chester");
        try {
            FileWriter fw = new FileWriter(chesterFile, true);
            BufferedWriter bw = new BufferedWriter(fw);
            for (String sentence : this.newSentences) {
                bw.write(sentence + "\n");
            }
            bw.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
        }

    }

    public void addToFile(String sentence) {
        this.newSentences.add(sentence);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if(player.isOp() && UPDATE) {
            player.sendMessage(ChatColor.DARK_AQUA + "Version " + NEWVERSION + " of Chester is up for download!");
            player.sendMessage(ChatColor.DARK_AQUA + LINK + " to view the changelog and download!");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        //If alone or softmuted, don't log
        boolean isPlayerChattingToSelf = false;
        if (event.getRecipients().size() < 2 || ds.isSoftMuted(player.getUniqueId()))
            isPlayerChattingToSelf = true;
        final boolean doNotLog = isPlayerChattingToSelf;

        final AsyncChesterLogEvent cle = new AsyncChesterLogEvent(player, event.getMessage());
        getServer().getPluginManager().callEvent(cle);
        final String message = cle.getMessage();
        // Permissions checks aren't thread safe so we need to handle this on the main thread
        new BukkitRunnable() {
            public void run() {
                if(player.hasPermission("chester.log") && !cle.isCancelled() && !doNotLog) {
                    addToFile(clean(message));
                }
                if (player.hasPermission("chester.trigger")) {
                    boolean cancel = false;
                    for(String trigger:triggerwords) {
                        if(message.matches("^.*(?i)" + trigger + ".*$")) {
                            cancel = true;
                            break;
                        }
                    }
                    if(!cancel && !doNotLog) {
                        Chester.this.chester.addSentenceToBrain(message);
                    }
                }
            }
        }.runTask(this);
        this.chester.queueMessage(message);
    }
}
