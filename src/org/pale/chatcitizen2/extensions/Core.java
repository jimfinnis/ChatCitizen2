package org.pale.chatcitizen2.extensions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;

import net.citizensnpcs.api.npc.NPC;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.pale.chatcitizen2.ChatTrait;
import org.pale.chatcitizen2.MaterialNameParser;
import org.pale.simplechat.Conversation;
import org.pale.simplechat.actions.ActionException;
import org.pale.simplechat.actions.Cmd;
import org.pale.simplechat.actions.Value;
import org.pale.simplechat.values.IntValue;
import org.pale.simplechat.values.NoneValue;
import org.pale.simplechat.values.StringValue;


// extensions using core Bukkit.
public class Core {

	// mctime (string -- timestring) get minecraft time. Input is digital,approx or todstring; anything else gives minecraft ticks. 
	@Cmd public static void mctime(Conversation c) throws ActionException{
		String type = c.popString();
		NPC npc  = ((ChatTrait)c.instance.getData()).getNPC();

		String out;
		long t = npc.getEntity().getWorld().getTime();
		int hours = (int) ((t / 1000 + 6) % 24);
		int minutes = (int) (60 * (t % 1000) / 1000);
		if(type==null)type="digital";
		if(type.equals("digital")){
			out=String.format("%02d:%02d", hours,minutes);
		} else if(type.equals("todstring")) {
			if(t>22000 || t<6000)
				out = "morning";
			else if(t>=6000 && t<11500)
				out = "afternoon";
			else if(t>=11500 && t<15000)
				out = "evening";
			else out = "night";
		} else if(type.equals("approx")){
			if (t > 22700 || t <= 450) {
				out="dawn";
			} else if (t > 4000 && t <= 8000) {
				out="noon";
			} else if (t > 11500 && t <= 13500) {
				out="dusk";
			} else if (t > 16000 && t <= 20000) {
				out="midnight";
			} else if (t > 12000) {
				out="night";
			} else
				out="day";
		} else out = Long.toString(t);

		c.push(new StringValue(out));
	}

	static Instant startTime = Instant.now();

	// get REAL time in seconds since server boot (well, plugin start)
	@Cmd public static void realnow(Conversation c) throws ActionException {
		long t = ChronoUnit.SECONDS.between(startTime, Instant.now());
		c.push(new IntValue((int)t));
	}

	// get BUKKIT TIME in ticks for the NPC's world
	@Cmd public static void now(Conversation c) throws ActionException {
		ChatTrait ct = (ChatTrait)c.instance.getData();
		long t = ct.getNPC().getEntity().getWorld().getFullTime();
		c.push(new IntValue((int)t));
	}

	// broadcast (string --) write a message directly to all players. Useful in debugging, can be used inside a timer. 
	@Cmd public static void broadcast(Conversation c) throws ActionException {
		String msg = c.popString();
		for(Player p : Bukkit.getOnlinePlayers()){
			p.sendMessage(msg);
		}
	}

	// rain (-- boolean 1 or 0) is it raining/snowing 
	@Cmd public static void rain(Conversation c) throws ActionException {
		ChatTrait ct = (ChatTrait)c.instance.getData();
		World w =ct.getNPC().getEntity().getWorld();
		c.push(new IntValue(w.hasStorm() ? 1 : 0));
	}

	// utter (string --) say something without using the input/response system, typically in response to a timer
	@Cmd public static void utter(Conversation c) throws ActionException {
		ChatTrait ct = (ChatTrait)c.instance.getData();
		String msg = c.popString();
		ct.utter("(nearby)",msg);
	}


	// take (count itemname -- result) attempt to move items from the player's main hand, typically 
	// from a RIGHTCLICK event.
	// Results: NOTENOUGH (player doesn't have the number we requested)
	//          UNKNOWN (couldn't parse the itemname specified into a minecraft item id)
	//          NOITEM (player tried to give me nothing, i.e. was holding air)
	//          WRONG (player tried to give the wrong item)
	//			OK ( everything worked)
	// Note that this does not add items to the bot, it just removes them from the player!
	@Cmd public static void take(Conversation c) throws ActionException {
		String itemName = c.popString();
		int count = c.pop().toInt();

		String out;

		Material m = MaterialNameParser.get(itemName);
		if(m==null)
			out = "UNKNOWN";
		else {
			Player p = (Player)c.source; // cast conversation source back to Player
			ItemStack st = p.getInventory().getItemInMainHand();
			if(st.getType() == Material.AIR) out = "NOITEM";
			else if(st.getType()!=m) out = "WRONG";
			else {
				int newamount = st.getAmount() - count;
				if(newamount<0)out = "NOTENOUGH";
				else {
					if(newamount==0)
						p.getInventory().setItemInMainHand(null);
					else
						st.setAmount(newamount);
					out = "OK";
				}
			}
		}
		c.push(new StringValue(out));
	}


	// give (count itemname -- result) attempt to add items to the player. Does not remove items from the bot.
	// If there's no room in the player's inventory, the items will be put on the ground.
	// Results: UNKNOWN (couldn't parse the itemname specified into a minecraft item id)
	//			OK ( everything worked)
	//		
	@Cmd public static void give(Conversation c) throws ActionException {
		String itemName = c.popString();
		int count = c.pop().toInt();

		String out;

		Material m = MaterialNameParser.get(itemName);
		if(m==null)
			out = "UNKNOWN";
		else {
			Player p = (Player)c.source; // cast conversation source back to Player
			ItemStack st = new ItemStack(m,count);
			PlayerInventory inv = p.getInventory();
			HashMap<Integer,ItemStack> couldntStore = inv.addItem(st);

			// drop remaining items at the player
			for(ItemStack s: couldntStore.values()){
				p.getWorld().dropItem(p.getLocation(), s);
			}
			out = "OK";
		}
		c.push(new StringValue(out));
	}

	@Cmd public static void itemheld(Conversation c) throws ActionException {

	}

	// matname (string -- string) convert a material name to a standard Minecraft name (or none)
	@Cmd public static void matname(Conversation c) throws ActionException {
		String name = c.popString();
		Material m = MaterialNameParser.get(name);
		c.push(m==null ? NoneValue.instance : new StringValue(m.name().toLowerCase()));
	}

	// addtimer (seconds name -- id) add a timer function, throws exception if no func exists
	@Cmd public static void addtimer(Conversation c) throws ActionException {
		String name = c.popString();
		int interval = c.pop().toInt();
		ChatTrait t = (ChatTrait)c.instance.getData();
		if(c.instance.bot.hasFunc(name)){
			int id = t.addTimer(name,interval);
			c.push(new IntValue(id));
		} else
			throw new ActionException("Unknown function for timer : "+name);
	}

	// removetimer (id --) remove a timer
	@Cmd public static void removetimer(Conversation c) throws ActionException {
		int id = c.pop().toInt();
		ChatTrait t = (ChatTrait)c.instance.getData();
		t.removeTimer(id);
	}

	// json (string -- jsonbuilder) create a new JSON chat text with an initial element
	@Cmd public static void json(Conversation c) throws ActionException {
		c.push(new JSONBuilderValue(c.popString()));
	}

	// jsoncol (jsonbuilder colorname -- jsonbuilder) tint the last added item
	@Cmd public static void jsoncol(Conversation c) throws ActionException {
		String col = c.popString().toLowerCase();
		Value v = c.peek();
		ChatColor cc;
		if(v instanceof JSONBuilderValue) {
			try {
				cc = ChatColor.valueOf(col.toUpperCase());
			} catch(IllegalArgumentException e){
				throw new ActionException("Unknown colour code "+col);
			}
			JSONBuilderValue b = (JSONBuilderValue) v;
			b.b.color(cc);
		} else throw new ActionException("Not a JSON builder");
	}

	// jsonbold (jsonbuilder bool -- jsonbuilder) turn bold on/off
	@Cmd public static void jsonbold(Conversation c) throws ActionException {
		boolean on = c.popBoolean();
		Value v = c.peek();
		if(v instanceof JSONBuilderValue) {
			JSONBuilderValue b = (JSONBuilderValue) v;
			b.b.bold(on);
		} else throw new ActionException("Not a JSON builder");
	}

	// jsonitalic (jsonbuilder bool -- jsonbuilder) italic on/off
	@Cmd public static void jsonitalic(Conversation c) throws ActionException {
		boolean on = c.popBoolean();
		Value v = c.peek();
		if(v instanceof JSONBuilderValue) {
			JSONBuilderValue b = (JSONBuilderValue) v;
			b.b.italic(on);
		} else throw new ActionException("Not a JSON builder");
	}

	// jsonclick (jsonbuilder text -- jsonbuilder) make the last added item clickable, which will say the thing
	@Cmd public static void jsonclick(Conversation c) throws ActionException {
		String txt = c.popString().toLowerCase();
		Value v = c.peek();
		if(v instanceof JSONBuilderValue) {
			JSONBuilderValue b = (JSONBuilderValue) v;
			b.b.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,txt));
		} else throw new ActionException("Not a JSON builder");
	}



	//  sendjson (jsonbuilder --) sends a JSON chat text, but only to a player
	@Cmd public static void jsonsend(Conversation c) throws ActionException {
		Value v = c.pop();
		if(v instanceof JSONBuilderValue) {
			JSONBuilderValue b = (JSONBuilderValue) v;
			if (c.source instanceof Player) {
				Player p = (Player) c.source;
				p.spigot().sendMessage(b.b.create());
			}
		} else throw new ActionException("Not a JSON builder");
	}

	// lastseen (playername -- time) return how long ago (in mins) a player was seen, or -1.
	@Cmd public static void lastseen(Conversation c) throws ActionException {
		String name = c.popString();
		ChatTrait ct = (ChatTrait)c.instance.getData();
		c.push(new IntValue(ct.getTimeSeen(name)));
	}
}
