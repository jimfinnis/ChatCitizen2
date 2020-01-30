package org.pale.chatcitizen2.extensions;

import net.md_5.bungee.api.chat.ComponentBuilder;
import org.pale.simplechat.actions.ActionException;
import org.pale.simplechat.actions.BinopInstruction;
import org.pale.simplechat.actions.Value;
import org.pale.simplechat.values.StringValue;

public class JSONBuilderValue extends Value {
    public ComponentBuilder b;

    public JSONBuilderValue(String baseString){
        b = new ComponentBuilder(baseString);
    }
    @Override public String str(){
        return "JSON-"+hashCode();
    }


    // add to a JSON string the usual way
    @Override public Value binop(BinopInstruction.Type t, Value snd) {
        b.append(snd.str());
        return this;
    }
}
