package org.pale.chatcitizen2;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Map.Entry;

import net.citizensnpcs.api.persistence.Persist;
import net.citizensnpcs.api.persistence.PersistenceLoader;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitName;
import net.citizensnpcs.api.util.DataKey;

import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.pale.simplechat.Bot;
import org.pale.simplechat.BotConfigException;
import org.pale.simplechat.BotInstance;
import org.pale.simplechat.Conversation;
import org.pale.simplechat.actions.Value;
import org.pale.simplechat.values.NoneValue;
import org.pale.simplechat.values.StringValue;


//This is your trait that will be applied to a npc using the /trait mytraitname command. 
//Each NPC gets its own instance of this class.
//the Trait class has a reference to the attached NPC class through the protected field 'npc' or getNPC().
//The Trait class also implements Listener so you can add EventHandlers directly to your trait.
@TraitName("chatcitizen") // convenience annotation in recent CitizensAPI versions for specifying trait name
public class ChatTrait extends Trait {

	private static final long sayCheckInterval = 5000; //!< how often (in ms) we check random say.

	public ChatTrait() {
		super("chatcitizen");
		plugin = JavaPlugin.getPlugin(Plugin.class);
	}

	static Random rand = new Random();

	Plugin plugin = null;
	@Persist String botName = null; //!< name of the bot in config.yml

	/**
	 * In the discussion below, "dist" means horizontal XZ distance, Y distance must always be < 2.
	 * The bot will say RANDSAY to the player 
	 *  - if sayInterval has passed since the last time something was said
	 *  - if random [0:1] < sayProbability (checked when the last check passes)
	 * 	- if dist < sayDist for some player (picked at random)
	 * Will do nothing if the pattern RANDSAY has no category. 
	 */
	@Persist public double sayInterval = 20; //!< min time between the bot saying stuff randomly
	@Persist public double sayProbability = 0.3; //!< chance the NPC will try to speak each sayInterval
	@Persist public double sayDist = 10; //!< how far the bot will look for someone to randomly talk at.
	/**
	 * Say GREETSAY when the dist (see above) drops below greetDist having been above greetDist for
	 * greetTime seconds.
	 * Will do nothing if the pattern GREETSAY has no category. 
	 */
	@Persist public double greetDist = 3; //!< how close a player should be before greet
	@Persist public double greetInterval = 20; //!< how long between greeting each player
	@Persist public double greetProbability = 0.9; //!< how likely is it we will greet a player? If this fails, we just ignore them.

	@Persist public double audibleDistance=10; //!< how far this robot is audible
	
	@Persist public int spawnCount=0; //!< number of times spawned

	/**
	 * Time at which we last saw a player, given their nick. Yes, you
	 * can disguise yourself by changing nick.
	 */
	Map<String, Instant> playerLastSawTime = new HashMap<String, Instant>();

	static class PersistedVars {
		Map<String,Value> vars;
		PersistedVars(Map<String,Value> m) {
			vars = m;
		}
	}
	@Persist
	public PersistedVars persistedVars = null;
	
	static {
		PersistenceLoader.registerPersistDelegate(PersistedVars.class,ValueMapPersister.class);
	}
	
	

	// the actual chatbot
	BotInstance instance = null;
	private long lastRandSay;
	
	public String getBotName(){
		return botName;
	}
	
	List<Player> getNearPlayers(double d){
		List<Player> r = new ArrayList<Player>();
		// note the 1 - we have to be roughly on the same level, AND we have to be able to see them.
		// Actually, we check to see if they can see *us*.
		for(Entity e: npc.getEntity().getNearbyEntities(d,1,d)){
			if(e instanceof Player){
                            Player p = (Player)e;
                            // now, I'm going to recycle this bit of code so we can store
                            // when we last saw a player! We only "see" a player when we try
                            // to talk, which is semantically odd, but it should work.
                            playerLastSawTime.put(p.getName().toLowerCase(), Instant.now());
                            if(p.hasLineOfSight(npc.getEntity())) {
                                r.add(p);
                                //Plugin.log("ADDING: "+p.getDisplayName());
                            }
			}
		}
		return r;
	}

	/**
	 * Get the last time I "saw" this player 
	 * @param player
	 * @return time difference in minutes, or -ve if never - max is 32000.
	 */
	public int getTimeSeen(String player){
            //Plugin.log("LOOKING FOR : "+player);
            player = player.toLowerCase();
		if(playerLastSawTime.containsKey(player)) {
			long diffInSeconds = ChronoUnit.SECONDS.between(playerLastSawTime.get(player),Instant.now());
			Plugin.log("GOT : "+diffInSeconds);
			if(diffInSeconds>32000)diffInSeconds=32000;
			return (int)diffInSeconds;
		} else {
			return -1; // i.e. in the future!
		}
	}
	
	

	// 
	// Here you should load up any values you have previously saved (optional). 
	// This does NOT get called when applying the trait for the first time, only loading onto an existing npc at server start.
	// This is called AFTER onAttach so you can load defaults in onAttach and they will be overridden here.
	// This is called BEFORE onSpawn, npc.getBukkitEntity() will return null.
	public void load(DataKey key) {
//		SomeSetting = key.getBoolean("SomeSetting", false);
		
	}

	// Save settings for this NPC (optional). These values will be persisted to the Citizens saves file
	public void save(DataKey key) {
//		key.setBoolean("SomeSetting",SomeSetting);
	}

	@EventHandler
	public void click(net.citizensnpcs.api.event.NPCRightClickEvent event){
		//Handle a click on a NPC. The event has a getNPC() method. 
		//Be sure to check event.getNPC() == this.getNPC() so you only handle clicks on this NPC!
		if(event.getNPC() == this.getNPC()){
			// this is where we trap a "give" action or suchlike
			if(instance.bot.hasFunc("RIGHTCLICK")){
				Player p = event.getClicker();
				ItemStack held = p.getInventory().getItemInMainHand();
				// shouldn't be necessary, but it does seem odd that an empty hand is full of air...
				String hstr = (held==null)?"NOITEM":held.getType().toString();
				hstr = hstr.toLowerCase();
				if(hstr.equals("air"))hstr="NOITEM";
				
				Conversation c = instance.getConversation(p);
				c.setVar("itemheld",new StringValue(hstr));
				respondToFunc("RIGHTCLICK", p);
			} else
				Plugin.log("does not have RC");
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void monitorDamageFromEntity(final net.citizensnpcs.api.event.NPCDamageByEntityEvent e){
		if(e.getNPC() == this.getNPC()){
			Entity bastard = e.getDamager();
			if(instance.bot.hasFunc("PLAYERHITME")){
				if(bastard instanceof Player){
					Player p = (Player)bastard;
					respondToFunc("PLAYERHITME", p);
				}
			} else {
				if(instance.bot.hasFunc("ENTITYHITME"))
					respondToFunc("ENTITYHITME",null);
			}
		}
	}

	@EventHandler(priority=EventPriority.MONITOR)
	public void monitorDamageEntity(final net.citizensnpcs.api.event.NPCDamageEntityEvent e){
		if(e.getNPC() == this.getNPC()){
			if(instance.bot.hasFunc("HITSOMETHING"))respondToFunc("HITSOMETHING",null);
		}
	}



	private int tickint=0;
	public long timeSpawned=0;
	
	public class Timer {
		private String fname;
		private long interval;
		private long ct;
		public Timer(String f,long interval){
			this.fname = f;
			this.interval = interval;
			ct=interval;
		}
		
		private void tick(){
			if(--ct <=0){
				ct = interval;
				respondToFunc(fname, null);
			}
		}
	}
	private Map<Integer,Timer> timers = new HashMap<Integer,Timer>();
	static int timerIdCt=0;
	public int addTimer(String fname,int interval){
		timers.put(timerIdCt,new Timer(fname,interval));
		return timerIdCt++;
	}
	private List<Integer> timersToRemove = new ArrayList<Integer>();
	public void removeTimer(int t){
		if(timers.containsKey(t))
			timersToRemove.add(t);
		else
			Plugin.log("Cannot remove timer "+t+" on "+npc.getFullName());
	}

	// Called every tick
	@Override
	public void run() {
		if(tickint++==20){ // to reduce CPU usage - this is about 1Hz.
			processRandSay();
			processGreetSay();
			tickint=0;
			for(Entry<Integer, Timer> t: timers.entrySet())
				t.getValue().tick();
			for(int t: timersToRemove){
				timers.remove(t);
			}
			timersToRemove.clear();
		}
		timeSpawned++;
	}

	//Run code when your trait is attached to a NPC. 
	//This is called BEFORE onSpawn, so npc.getBukkitEntity() will return null
	//This would be a good place to load configurable defaults for new NPCs.
	@Override
	public void onAttach() {
		plugin.getServer().getLogger().info(npc.getName() + " has been assigned ChatCitizen!");
	}

	/**
	 * Change the bot. If reset is true, will cause the spawn count to be reset and the 
	 * init clauses to run - typically done when changing the bot by a command.
	 * @param b
	 * @param name 
	 * @param reset
	 */
	public void setBot(Bot b, String name, boolean reset){
		try {
			botName = name;
			if(instance!=null)instance.remove();
			instance = new BotInstance(b,npc.getFullName(),this);
			// if this is a new bot it won't have any persisted vars. Use those in the bot instance,
			// set up by init. Otherwise, use those in the persistence data.
			if(reset || persistedVars == null || persistedVars.vars.size()==0) {
				persistedVars = new PersistedVars(instance.getVars());
			} else {
				instance.setVars(persistedVars.vars);
			}
			if(reset)spawnCount=0;
			if(spawnCount==0)
				instance.runInits();
			spawnCount++;
		} catch (BotConfigException e) {
			e.printStackTrace();
			Plugin.log("cannot configure bot "+b.getName());
		}
	}

	// Run code when the NPC is despawned. This is called before the entity actually despawns so npc.getBukkitEntity() is still valid.
	@Override
	public void onDespawn() {
//		Plugin.log(" Despawn run on "+npc.getFullName());
		instance.remove();
		instance=null;
		plugin.removeChatter(npc);
		
	}

	//Run code when the NPC is spawned. Note that npc.getBukkitEntity() will be null until this method is called.
	//This is called AFTER onAttach and AFTER Load when the server is started.
	@Override
	public void onSpawn() {
		if(botName==null)botName="default"; // this really shouldn't be required.

		Bot b = plugin.getBot(botName);
		if(b==null)
			throw new RuntimeException("bot \""+botName+"\" not found - is it in the config?");

		setBot(b,botName,false);

		plugin.addChatter(npc);
//		Plugin.log(" Spawn run on "+npc.getFullName());
	}

	//run code when the NPC is removed. Use this to tear down any repeating tasks.
	@Override
	public void onRemove() {
	}

	/**
	 * say something out-of-band, directly.
	 * @param toName
	 * @param pattern
	 */
	public void utter(String toName,String msg){
		List<Player> q = getNearPlayers(audibleDistance);
		if(q.size()>0){
			// if a zero length string is returned, nothing happens.
			if(msg.trim().length()!=0){
				String s = ChatColor.AQUA+"["+npc.getFullName()+" -> "+toName+"] "+ChatColor.WHITE+msg;
				for(Player p: q){
					p.sendMessage(s);
				}
			}
		}
	}
	/**
	 * Generate and send a response to a list of players. p (the player responded to) may be null.
	 * 
	 */
	private void say(Player inResponseTo,String toName,String pattern){
		List<Player> q = getNearPlayers(audibleDistance);
		if(q.size()>0){
			String msg = instance.handle(pattern, inResponseTo);
			// if a zero length string is returned, nothing happens.
			if(msg.trim().length()!=0){
				String s = ChatColor.AQUA+"["+npc.getFullName()+" -> "+toName+"] "+ChatColor.WHITE+msg;
				for(Player p: q){
					p.sendMessage(s);
				}
			}
		}
	}

	/**
	 * Perform a user function in the bot and say the result. NOTE THAT the func will ALWAYS run whether
	 * there's anyone to hear it or not.
	 * @param fname funcname
	 * @param source player who caused the event which triggered this (sets convvars in bot) OR NONE, in which case it's a general message and from the bot.
	 */
	private void respondToFunc(String fname, Player source) {
		String toName;
		Plugin.log("attempting "+fname);

		if(source!=null){
			setPropertiesForSender(source);
			toName = source.getDisplayName();
		} else {
			setPropertiesForNone();
			toName = "(nearby)";
		}
		String msg = instance.runFunc(fname, source!=null?source:this); // if source is null, cast to this (any object will do, really).
		if(msg!=null){
			List<Player> q = getNearPlayers(audibleDistance);
			if(q.size()>0){
				if(msg.trim().length()!=0){
					String s = ChatColor.AQUA+"["+npc.getFullName()+" -> "+toName+"] "+ChatColor.WHITE+msg;
					for(Player p: q){
						p.sendMessage(s);
					}
				}
			}
		}
	}

	/**
	 * Respond to a player saying something nearby. Alternatively used to just say something randomly,
	 * in which case the player argument is to whom it should be said and the string is a special pattern (like RANDSAY).
	 * @param player the player who spoke
	 * @param input what they said
	 */
	public void respondTo(Player player,String input) {
		setPropertiesForSender(player);
		say(player,player.getDisplayName(),input);
	}

	/**
	 * Say something (typically a spontaneous speech) to everyone nearby. The msg is passed to be bot,
	 * and should be a special (RANDSAY etc.). 
	 */
	public void sayToAll(String pattern){
		say(null,"(nearby)",pattern);
	}

	/**
	 * Set properties within the chat bot based on the player who has just spoken to it.
	 * @param player
	 */
	public void setPropertiesForSender(Player player) {
		// sets an instance var, not a conv var...
		instance.setVar("name", new StringValue(player.getDisplayName()));
	}
	
	/**
	 * If a message is being sent to the general area with sayFuncToAll(),
	 * set properties accordingly.
	 */
	public void setPropertiesForNone(){
		// sets an instance var, not a conv var...
		instance.setVar("name",NoneValue.instance);
		
	}

	private long lastSayCheckIntervalTime=0;
	private void processRandSay(){
		if(instance.bot.hasFunc("RANDSAY")){
			long t = System.currentTimeMillis();
			if(t-lastSayCheckIntervalTime > sayCheckInterval){
				if((t-lastRandSay > sayInterval*1000) && (rand.nextDouble()<sayProbability)){
					// try to find someone to talk to
					List<Player> ps  = getNearPlayers(sayDist);
					if(ps.size() > 0){
						// pick one at random.
						Player p = ps.get(rand.nextInt(ps.size()));
						respondToFunc("RANDSAY",p);
						lastRandSay = t;
					}
				}
				lastSayCheckIntervalTime = t;
			}
		}
	}

	Map<String,Long> lastGreeted = new HashMap<String,Long>();

	List<Player> nearPlayersForGreet  = new ArrayList<Player>();
	private void processGreetSay(){
		if(instance.bot.hasFunc("GREETSAY")){
			List<Player> nearPlayersNew = getNearPlayers(greetDist);
			for(Player p : nearPlayersNew){
				// is this someone who has just appeared?
				if(!nearPlayersForGreet.contains(p)){
					long lasttime;
					long t = System.currentTimeMillis();
					if(lastGreeted.containsKey(p.getName()))
						lasttime = lastGreeted.get(p.getName());
					else
						lasttime = 0;
					// we didn't greet them recently; let's do that.
					if(t-lasttime > greetInterval*1000){
						if(rand.nextDouble()<greetProbability){
							respondToFunc("GREETSAY",p);
						}
						lastGreeted.put(p.getName(), t);
					}
				}
			}
			nearPlayersForGreet = nearPlayersNew;
		}
	}



	/**
	 * Used in the "t" test command
	 * @param msg
	 * @return
	 */
	public String getResponseTest(String msg) {
		return instance.handle(msg,new Object());
	}
}
