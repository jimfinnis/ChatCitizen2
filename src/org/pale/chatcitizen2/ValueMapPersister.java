package org.pale.chatcitizen2;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import net.citizensnpcs.api.persistence.Persister;
import net.citizensnpcs.api.util.DataKey;

import org.pale.simplechat.actions.Value;
import org.pale.simplechat.values.DoubleValue;
import org.pale.simplechat.values.IntValue;
import org.pale.simplechat.values.ListValue;
import org.pale.simplechat.values.MapValue;
import org.pale.simplechat.values.NoneValue;
import org.pale.simplechat.values.RangeValue;
import org.pale.simplechat.values.StringValue;

/**
 * Persists *some* value types (not cats or patterns).
 * @author white
 *
 */
public class ValueMapPersister implements Persister<ChatTrait.PersistedVars> {
	
	private static final int INT = 0;
	private static final int LIST = 1;
	private static final int STRING = 2;
	private static final int DOUBLE = 3;
	private static final int RANGE = 4;
	private static final int MAP = 5;

	

	public Value createValue(DataKey root) {
		switch(root.getInt("type")){
		case INT:
			return new IntValue(root.getInt("val"));
		case LIST:
			ListValue lv = new ListValue();
			for(int i=0;i<root.getInt("size");i++){
				DataKey k = root.getRelative(i);
				lv.list.add(createValue(k));
			}
			return lv;
		case STRING:
			return new StringValue(root.getString("val"));
		case DOUBLE:
			return new DoubleValue(root.getDouble("val"));
		case RANGE:
			return new RangeValue(root.getInt("start"),root.getInt("end"));
		case MAP:
			MapValue mv = new MapValue();
			DataKey maproot = root.getRelative("val");
			for(DataKey k : maproot.getSubKeys()){
				Value v = createValue(k);
				if(v!=null)
					mv.map.put(k.name(), v);
			}
			return mv;
		default:
			Plugin.log("Attempt to retrieve a bad type, returning none instead");
			return null;
		}
	}

	public void saveValue(Value v, DataKey root) {
		// save into a DataKey.
		root.removeKey("val");
		root.removeKey("size");
		if(v instanceof StringValue){
			root.setInt("type", STRING);
			root.setString("val",v.str());
		} else if(v instanceof ListValue){
			root.setInt("type", LIST);
			ListValue lst = (ListValue)v;
			root.setInt("size",lst.list.size());
			for(int i=0;i<lst.list.size();i++){
				DataKey k = root.getRelative(i);
				saveValue(lst.list.get(i),k);
			}
		} else if(v instanceof IntValue) {
			root.setInt("type", INT);
			root.setInt("val", ((IntValue)v).toInt());
		} else if(v instanceof DoubleValue) {
			root.setInt("type", DOUBLE);
			root.setDouble("val", ((DoubleValue)v).toDouble());
		} else if(v instanceof RangeValue){
			root.setInt("type", RANGE);
			RangeValue r = (RangeValue)v;
			root.setInt("start", r.start);
			root.setInt("end", r.end);
		} else if(v instanceof MapValue){
			root.setInt("type", MAP);
			DataKey maproot = root.getRelative("val");
			MapValue map = (MapValue)v;
			for(Entry<String,Value> e: map.map.entrySet()){
				saveValue(e.getValue(),maproot.getRelative(e.getKey()));
			}
		}
	}

	@Override
	public ChatTrait.PersistedVars create(DataKey root) {
		Map<String,Value> map = new HashMap<String,Value>();
		for(DataKey k : root.getSubKeys()){
			Value v = createValue(k);
			if(v!=null)
				map.put(k.name(), v);
		}
		return new ChatTrait.PersistedVars(map);
	}

	@Override
	public void save(ChatTrait.PersistedVars pv, DataKey root) {
		for(Entry<String,Value> e: pv.vars.entrySet()){
			saveValue(e.getValue(),root.getRelative(e.getKey()));
		}
	}

}
