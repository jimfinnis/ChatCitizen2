package org.pale.chatcitizen2;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.pale.chatcitizen2.Command.CallInfo;
import org.pale.chatcitizen2.Command.Cmd;
import org.pale.chatcitizen2.Command.Registry;
import org.pale.chatcitizen2.extensions.Core;
import org.pale.chatcitizen2.extensions.NPCDest;
import org.pale.chatcitizen2.plugininterfaces.NPCDestinations;
import org.pale.chatcitizen2.plugininterfaces.Sentinel;
import org.pale.simplechat.Bot;
import org.pale.simplechat.BotConfigException;
import org.pale.simplechat.Conversation;
import org.pale.simplechat.Logger;
import org.pale.simplechat.ParserError;
import org.pale.simplechat.Tokenizer;
import org.pale.simplechat.actions.ActionException;
import org.pale.simplechat.actions.InstructionCompiler;
import org.pale.simplechat.actions.InstructionStream;
import org.pale.simplechat.actions.Value;
import org.pale.simplechat.values.DoubleValue;
import org.pale.simplechat.values.IntValue;
import org.pale.simplechat.values.StringValue;



public class Plugin extends JavaPlugin {
	public static void log(String msg) {
		getInstance().getLogger().info(msg);
	}
	public static void warn(String msg) {
		getInstance().getLogger().warning(msg);
	}
	/**
	 * Make the plugin a weird singleton.
	 */
	static Plugin instance = null;

	/**
	 * All the bot wrappers - the Traits share the bots.
	 */
	private Map<String,Bot> bots = new HashMap<String,Bot>();

	public NPCDestinations ndPlugin;
	public Sentinel sentinelPlugin;

	private Registry commandRegistry=new Registry();

	/**
	 * Use this to get plugin instances - don't play silly buggers creating new
	 * ones all over the place!
	 */
	public static Plugin getInstance() {
		if (instance == null)
			throw new RuntimeException(
					"Attempt to get plugin when it's not enabled");
		return instance;
	}

	@Override
	public void onDisable() {
		instance = null;
		getLogger().info("ChatCitizen has been disabled");
	}

	public Plugin(){
		super();
		if(instance!=null)
			throw new RuntimeException("oi! only one instance!");
		InstructionCompiler.register(Core.class);
		InstructionCompiler.register(NPCDest.class);
		InstructionCompiler.register(org.pale.chatcitizen2.extensions.Sentinel.class);
		InstructionCompiler.addExtension("ChatCitizen");
	}

	@Override
	public void onEnable() {
		instance = this;
		//check if Citizens is present and enabled.

		if(getServer().getPluginManager().getPlugin("Citizens") == null || getServer().getPluginManager().getPlugin("Citizens").isEnabled() == false) {
			getLogger().severe("Citizens 2.0 not found or not enabled");
			getServer().getPluginManager().disablePlugin(this);	
			return;
		}

		org.pale.simplechat.Logger.setListener(new org.pale.simplechat.Logger.Listener() {
			@Override
			public void log(String s) {
				Plugin.log("-- "+s);
			}
		});

		// check other optional plugins
		ndPlugin = new NPCDestinations();

		sentinelPlugin = new Sentinel();

		// initialise AIML extensions
		//		AIMLProcessor.extension = new ChatBotAIMLExtension();

		// this is the listener for pretty much ALL events EXCEPT NPC events, not just chat.
		new ChatEventListener(this);


		//Register.        
		net.citizensnpcs.api.CitizensAPI.getTraitFactory().registerTrait(net.citizensnpcs.api.trait.TraitInfo.create(ChatTrait.class));	

		saveDefaultConfig();
		loadBots();
		commandRegistry.register(this); // register commands

		getLogger().info("ChatCitizen has been enabled");
	}

	public void loadBots(){
		Logger.setLog(Logger.ALL);
		FileConfiguration c = this.getConfig();
		final ConfigurationSection bots = c.getConfigurationSection("bots");
		if(bots==null){
			throw new RuntimeException("No bots section in config");
		}

		// tell the system to get the bot's filename from the config file
		Bot.PathProvider pf = new Bot.PathProvider() {
			@Override
			public Path path(String name) {
				String n = bots.getString(name);
				if(n==null) {
					log("Provider: req "+name+", result : THAT BOT DOES NOT EXIST. Add it to config.yml");
					return null;
				} else {
					log("Provider: req "+name+", result "+n);
					return Paths.get(bots.getString(name));
				}
			}
		};
		Bot.setPathProvider(pf);

		for(String name : bots.getKeys(false)){
			try {
				this.bots.put(name,Bot.loadBot(name));
			} catch (BotConfigException e) {

				// this is a Cunning Ruse to let us use a console colour in our log messages.
				ConsoleCommandSender console = getServer().getConsoleSender();

				console.sendMessage(ChatColor.RED+"##################################################################################");
				console.sendMessage(ChatColor.RED+"cannot load bot "+name+", error: "+e.getMessage());
				console.sendMessage(ChatColor.RED+"##################################################################################");
			}
		}
		log("Bots all loaded.");
		Logger.setLog(0);
	}

	public static void sendCmdMessage(CommandSender s,String msg){
		s.sendMessage(ChatColor.AQUA+"[ChatCitizen] "+ChatColor.YELLOW+msg);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {
		String cn = command.getName();
		if(cn.equals("chatcitizen")){
			commandRegistry.handleCommand(sender, args);
			return true;
		}
		return false;
	}

	public Bot getBot(String s){
		if(bots.containsKey(s)){
			return bots.get(s);
		} else return null;
	}

	List<NPC> chatters = new ArrayList<NPC>();

	public void addChatter(NPC npc) {
		chatters.add(npc);
	}
	public void removeChatter(NPC npc){
		chatters.remove(npc);
	}

	public static boolean isNear(Location a,Location b,double dist){
		return (a.distance(b)<5 && Math.abs(a.getY()-b.getY())<2);
	}

	public void handleMessage(Player player, String msg){
		Location playerloc = player.getLocation();
		Vector playerpos = playerloc.toVector();
		Vector playerdir = playerloc.getDirection().normalize();
		for(NPC npc: chatters){
			Location npcl = npc.getEntity().getLocation();
			Vector npcpos = npcl.toVector();
			if(npc.hasTrait(ChatTrait.class)){
				if(isNear(playerloc,npcl,2)){ // chatters assume <2m and you're talking to them.
					Vector tonpc = npcpos.subtract(playerpos).normalize();
					// dot prod of facing vector and vector to player
					double dot = tonpc.dot(playerdir);
					//log("Dot to "+npc.getName()+ " is "+Double.toString(dot));
					// make sure we're roughly facing the NPC
					if(dot>0.8){
						ChatTrait ct = npc.getTrait(ChatTrait.class);
						ct.setPropertiesForSender(player);
						ct.respondTo(player,msg);
					}
				}
			}
		}
	}

	public static ChatTrait getChatCitizenFor(CommandSender sender) {
		NPC npc = CitizensAPI.getDefaultNPCSelector().getSelected(sender);
		if (npc == null) {
			return null;
		}
		if (npc.hasTrait(ChatTrait.class)) {
			return npc.getTrait(ChatTrait.class);
		}
		return null;
	}

	/**
	 * Commands
	 */

	@Cmd(desc="get info on a bot",argc=0,usage="<npcname>",cz=true)
	public void info(CallInfo c){
		String [] a;
		ChatTrait ct = c.getCitizen();
		a = new String[] {
				"Bot name = "+ct.getBotName(),
				"Random speech distance [saydist] = "+ct.sayDist,
				"Random speech interval [sayint] = "+ct.sayInterval,
				"Random speech chance [sayprob] = "+(int)(ct.sayProbability*100),
				"Greet distance [greetdist] = "+ct.greetDist,
				"Greet interval [greetint] = "+ct.greetInterval,
				"Greet chance = [greetprob] "+(int)(ct.greetProbability*100),
				"Audible distance [auddist] = "+ct.audibleDistance
		};

		StringBuilder b = new StringBuilder();
		for(String s: a){
			b.append(s);b.append("\n");
		}
		c.msg(b.toString());
	}

	@Cmd(desc="reload all bots",argc=0,permission="chatcitizen.reloadall")
	public void reloadall(CallInfo c){
		for(Bot b : bots.values()){
			try {
				b.reload();
			} catch (BotConfigException e) {
				c.msg(ChatColor.RED+"Bot reload failed: "+e.getMessage());
			}
		}	
		c.msg("Reload OK.");
	}

	@Cmd(desc="reload a given bot",argc=1,permission="chatcitizen.reload",usage="[botname]")
	public void reload(CallInfo c){
		String n = c.getArgs()[0];
		if(!bots.containsKey(n)){
			c.msg("Bot not known. List bots with \"ccz bots\".");
		} else {
			Bot b = bots.get(n);
			try {
				b.reload();
			} catch (BotConfigException e) {
				c.msg(ChatColor.RED+"Bot reload failed: "+e.getMessage());
			}
		}
		c.msg("Reload OK.");
	}

	@Cmd(name="bots",desc="list all bots and which NPCs use them",argc=0)
	public void listBots(CallInfo c){
		for(String s : bots.keySet()){
			Bot b = bots.get(s);
			StringBuilder sb = new StringBuilder();
			sb.append(ChatColor.AQUA+s+": "+ChatColor.GREEN);
			//			for(BotInstance i: b.getChats()){
			//				sb.append(chat.npc.getFullName()+" ");
			//			}
			sb.append("LIST NOT SUPPORTED");
			c.msg(sb.toString());
		}
	}

	@Cmd(name="t",desc="chat test",permission="chatcitizen.test",usage="[string]",cz=true)
	public void testBot(CallInfo c){
		ChatTrait ct = c.getCitizen();
		String msg = "";
		for(String s:c.getArgs())
			msg += s + " ";
		String m = ct.getResponseTest(msg);
		getLogger().info("RESPONSE :"+m);
	}



	private static String[] paramNames={"saydist","sayint","sayprob","greetdist","greetint","greetprob","auddist"};

	// same ordering as paramNames - these are the actual field names!
	private static String[] paramFields={"sayDist","sayInterval","sayProbability","greetDist","greetInterval",
		"greetProbability","audibleDistance"
	};

	@Cmd(desc="set a property in a bot",argc=-1,usage="<property> <value>", cz=true, permission="chatcitizen.set")
	public void set(CallInfo c) {
		if(c.getArgs().length < 2){
			StringBuilder b = new StringBuilder();
			b.append("Parameters are: ");
			for(String s: paramNames){
				b.append(s);b.append(" ");
			}
			c.msg(b.toString());
		} else {
			ChatTrait ct = c.getCitizen();
			String[] args=c.getArgs();
			for(int i=0;i<paramNames.length;i++){
				if(args[0].equals(paramNames[i])){
					try {
						Field f = ChatTrait.class.getDeclaredField(paramFields[i]);
						double val = Double.parseDouble(args[1]);
						if(paramNames[i].contains("prob")){
							val *= 0.01; // convert "prob"abilities from percentages.
						}
						f.setDouble(ct,val);
					} catch (NumberFormatException e) {
						c.msg("that is not a number");							
					} catch (NoSuchFieldException | SecurityException e) {
						c.msg("no such field - this shouldn't happen:"+paramFields[i]);
					} catch (IllegalArgumentException e) {
						c.msg("probably a type mismatch - this shouldn't happen:"+paramFields[i]);
					} catch (IllegalAccessException e) {
						c.msg("illegal access to field - this shouldn't happen:"+paramFields[i]);
					}
					return; // found and handled, so exit.
				}
			}
			c.msg("No parameter of that name found");
			return;
		}
	}

	@Cmd(desc="set a chatbot for an NPC",argc=1,usage="<botname>",cz=true,permission="chatcitizen.set")
	public void setbot(CallInfo c){
		String name = c.getArgs()[0];
		ChatTrait ct = c.getCitizen();
		Bot b = Plugin.getInstance().getBot(name);
		if(b==null){
			c.msg("\""+name+"\" is not installed on this server.");
		} else {
			ct.setBot(b,name,true);
			c.msg(ct.getNPC().getFullName()+" is now using bot \""+name+"\".");
		}
	}

	@Cmd(desc="set a special parameter for an NPC",argc=2,usage="<param name> <value>",cz=true,permission="chatcitizen.set")
	public void setparam(CallInfo c){
		// this calls SETPARAM if it is defined, which should be a function with the picture (val param -- [string]).
		// Both parameters are strings.
		// It operates like a runFunc, in that any value left on the stack is output, and if the stack is empty
		// the output buffer is output.
		// It's typically used to set bot-specific parameters.
		String name = c.getArgs()[0];
		String val = c.getArgs()[1];
		ChatTrait ct = c.getCitizen();
		Conversation conv = ct.instance.getConversation(ct);
		if(conv==null){
			c.msg(ChatColor.RED+"wut?");return;
		}
		try {
			conv.push(new StringValue(val));
			conv.push(new StringValue(name));
			String msg = conv.runFunc("SETPARAM");
			c.msg(ChatColor.AQUA+msg);
		} catch (ActionException e) {
			c.msg(ChatColor.RED+e.getMessage());
		}
	}

	@Cmd(desc="set logging bits",argc=1,usage="<logging bitmask>")
	public void setlog(CallInfo c){
		int flags = Integer.parseInt(c.getArgs()[0]);
		Logger.setLog(flags);
	}

	@Cmd(desc="show help for a command or list commands",argc=-1,usage="[<command name>]")
	public void help(CallInfo c){
		if(c.getArgs().length==0){
			commandRegistry.listCommands(c);
		} else {
			commandRegistry.showHelp(c,c.getArgs()[0]);
		}
	}

	@Cmd(desc="list all instance variables for the current NPC",cz=true)
	public void liv(CallInfo c){
		ChatTrait ct = c.getCitizen();
		Map<String,Value> vars = ct.instance.getVars();
		for(Entry<String, Value>e: vars.entrySet()){
			c.msg(ChatColor.AQUA+e.getKey()+": "+ChatColor.YELLOW+e.getValue().str());
		}
	}
	@Cmd(desc="set an instance variable",usage="<name> <type (i,d,s,v)> <value>",cz=true,permission="chatcitizen.set")
	public void siv(CallInfo c){
		ChatTrait ct = c.getCitizen();
		String[] args = c.getArgs();
		if(args.length<3){
			c.msg("need (at least) 3 args");return;
		}

		StringBuilder sb = new StringBuilder();
		for(int i=2;i<args.length;i++){
			sb.append(args[i]);sb.append(" ");
		}
		args[2] = sb.toString();


		Value v;
		switch(args[1].charAt(0)){
		case 'i':v = new IntValue(Integer.parseInt(args[2].trim()));break;
		case 'd':v = new DoubleValue(Integer.parseInt(args[2].trim()));break;
		case 's':v = new StringValue(args[2].trim());break;
		case 'v':
			// weird one, this. We compile the rest of the arguments as action lang and get what's on the stack.
			Conversation conv = ct.instance.getConversation(c.getPlayer());
			Tokenizer tok = new Tokenizer("command arg",new StringReader(args[2]));
			try {
				InstructionStream str = new InstructionStream(ct.instance.bot, tok);
				str.run(conv, true);
				v = conv.pop();
			} catch (IOException | ParserError | IllegalAccessException | IllegalArgumentException | InvocationTargetException | ActionException e) {
				c.msg("Compile failed: "+e.getMessage());
				return;
			}
			break;
		default:c.msg("Bad type");return;
		}
		c.msg("Setting to "+v.str());
		ct.instance.setVar(args[0], v);
	}
}
