/**
 *  JsonFile
 *  Copyright 22.02.2015 by Robert Mader, @treba123
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.tools.storage;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * This extends JSONObject to be a file which gets loaded and written to disk
 *
 */
public class JsonFile extends JSONObject {
	
	private File file;
	private PrivateKey private_key = null;
	private PublicKey public_key = null;
	private String key_method = null;
	private String peer_hash = null;

	public JsonFile(File file) throws IOException{
		super();
		this.file = file;
		if(this.file.exists()){
			JSONTokener tokener;
			tokener = new JSONTokener(new FileReader(file));
			putAll(new JSONObject(tokener));
		}
		else{
			this.file.createNewFile();
			commit();
		}
	}
	
	public File getFile() {
	    return this.file;
	}
	
	/**
	 * Write changes to file. It is not required that the user calls this method,
	 * however, if sub-objects of existing objects are modified, the user must handle
	 * file writings themself.
	 * @throws JSONException
	 */
	public void commit() throws JSONException {
		FileWriter writer;
		try {
			writer = new FileWriter(file);
			writer.write(this.toString());
			writer.close();
		} catch (IOException e) {
			throw new JSONException(e.getMessage());
		}
	}

	@Override
	public JSONObject put(String key, boolean value) throws JSONException {
		super.put(key, value);
		commit();
		return this;
	}
	
	@Override
	public JSONObject put(String key, double value) throws JSONException {
		super.put(key, value);
		commit();
		return this;
	}
	
	@Override
	public JSONObject put(String key, Collection<?> value) throws JSONException {
		super.put(key, value);
		commit();
		return this;
	}
	
	@Override
	public JSONObject put(String key, int value) throws JSONException {
		super.put(key, value);
		commit();
		return this;
	}
	
	@Override
	public JSONObject put(String key, long value) throws JSONException {
		super.put(key, value);
		commit();
		return this;
	}
	
	@Override
	public JSONObject put(String key, Map<?, ?> value) throws JSONException {
		super.put(key, value);
		commit();
		return this;
	}
	
	@Override
	public JSONObject put(String key, Object value) throws JSONException {
		super.put(key, value);
		commit();
		return this;
	}
	
	@Override
	public Object remove(String key) {
		super.remove(key);
		commit();
		return this;
	}
	
	public PrivateKey getPrivateKey(){
		return private_key;
	}
	
	public PublicKey getPublicKey(){
		return public_key;
	}
	
	public String getKeyMethod(){
		return key_method;
	}
	
	public String getPeerHash(){
		return peer_hash;
	}
	
	public void setPeerHash(){
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			md.update(public_key.getEncoded());
			peer_hash = Base64.getEncoder().encodeToString(md.digest());
			return;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		peer_hash = null;
	}
	
	public boolean loadPrivateKey(){
		if(!has("private_key") || !has("key_method")) return false;
		
		String encodedKey = getString("private_key");
		String algorithm = getString("key_method");
		
		try{
			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encodedKey));
		    PrivateKey priv = KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
			private_key = priv;
			key_method = algorithm;
			return true;
		}
	   	catch(NoSuchAlgorithmException | InvalidKeySpecException e){
	   		e.printStackTrace();
	   	}
		return false;
	}
	
	public boolean loadPublicKey(){
		if(!has("public_key") || !has("key_method")) return false;
		
		String encodedKey = getString("public_key");
		String algorithm = getString("key_method");
		
		PublicKey pub = decodePublicKey(encodedKey, algorithm);
		if(pub != null){
			public_key = pub;
			key_method = algorithm;
			setPeerHash();
			return true;
		}
		return false;
	}
	
	public boolean setPrivateKey(PrivateKey key, String algorithm){
		put("private_key", Base64.getEncoder().encodeToString(key.getEncoded()));
		private_key = key;
		put("key_method",algorithm);
		key_method = algorithm;
		return true;
	}
	
	public boolean setPublicKey(PublicKey key, String algorithm){
		put("public_key", Base64.getEncoder().encodeToString(key.getEncoded()));
		public_key = key;
		put("key_method",algorithm);
		key_method = algorithm;
		setPeerHash();
		return true;
	}
	
	public static PublicKey decodePublicKey(String encodedKey, String algorithm){
		try{
		    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey));
		    PublicKey pub = KeyFactory.getInstance(algorithm).generatePublic(keySpec);
		    return pub;
		}
	   	catch(NoSuchAlgorithmException | InvalidKeySpecException e){
	   		e.printStackTrace();
	   	}
		return null;
	}
}
