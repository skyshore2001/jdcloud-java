package com.demo;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;

import com.jdcloud.*;

public class JDUpload extends JDApiBase {
	
// TODO: read config from file
	// 配置选项
	public static int memorySize = 500 * KB; // 默认内存保存500K，超过则保存到文件
	public static long uploadSizeMax = 10 * MB; // 默认最大上传10M内容

	static class PicSize {
		int w, h;
		PicSize(int w1, int h1) {
			w = w1;
			h = h1;
		}
	}
	// thumb size for upload type:
	static Map<String, PicSize> UploadType = asMap(
		"default", new PicSize(360, 360),
		"user", new PicSize(128, 128),
		"store", new PicSize(200, 150)
	);

	// 设置允许上传的文件类型。以及下载时的MIME信息
	static Map<String, String> ALLOWED_MIME = asMap(
		"jpg", "image/jpeg",
		"jpeg", "image/jpeg",
		"png", "image/png",
		"gif", "image/gif",
		"txt", "text/plain",
		"pdf", "application/pdf",
		"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
		"doc", "application/msword",
		"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
		"xls", "application/vnd.ms-excel",
		"zip", "application/zip",
		"rar", "application/x-rar-compressed"
	);
	
	static Map<String, String> FILE_TAG = asMap(
		"\u00ff\u00d8\u00ff", "jpg",
		"\u0089PNG", "png",
		"GIF8", "gif"
	);

	// generate image/jpeg output. if out=null, output to stdout
	void resizeImage(File in, int w, int h, File out)
	{
		BufferedImage imageIn = null;
		try {
			imageIn = ImageIO.read(in);
		} catch (IOException e) {
		}
		if (imageIn == null)
			throw new MyException(E_PARAM, "fail to read image: " + in.getName(), "读取图片失败。请使用jpg/png/gif图片");
		
		int srch =imageIn.getHeight(null);
		int srcw =imageIn.getWidth(null);

		// 保持宽高协调
		if (srcw < srch) {
			int tmp = w;
			w = h;
			h = tmp;
		}
		// 保持等比例, 不拉伸
		int h1 = w * srch / srcw;
		if (h1 > h) {
			w = h * srcw / srch;
		}
		else {
			h = h1;
		}

		BufferedImage imageOut = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		imageOut.getGraphics().drawImage(imageIn, 0, 0, w, h, null);  
		String ext = FilenameUtils.getExtension(out.getName());
		boolean rv = false;
		try {
			rv = ImageIO.write(imageOut, ext, out);
		} catch (IOException e) {
		}
		if (rv == false)
			throw new MyException(E_PARAM, "cannot create image from: " + in.getName(), "写图片文件失败");
	}
	
	boolean isPic(String ext)
	{
		return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png");
	}
	
	String guessFileType(InputStream in)
	{
		byte[] bs = new byte[4];
		String s;
		try {
			in.read(bs);
			s = new String(bs, "ISO-8859-1");
			in.reset();
		} catch (Exception e1) {
			return null;
		}
		if (s.length() < 4)
			return null;
		for (Map.Entry<String, String> e: FILE_TAG.entrySet()) {
			if (s.startsWith(e.getKey()))
				return e.getValue();
		}
		return null;
	}

	@FunctionalInterface
	interface UploadHandler
	{
		Object exec(FileItem fi) throws Exception;
	}
	
/**<pre>
%fn handleUpload(handler) -> JsArray
%param handler(FileItem fi) -> Object 如果返回false，表示不用继续处理更多文件

每次处理的返回值最终通过JsArray数组返回。

	JsArray rv = handleUpload(fi -> {
		File f = new File("/tmp/1.txt");
		fi.write(f);
		// handle file
		return f;
	});
	
 */
	public JsArray handleUpload(UploadHandler handler) throws Exception
	{
		DiskFileItemFactory factory = new DiskFileItemFactory();
		ServletFileUpload upload = new ServletFileUpload(factory);
		//upload.setHeaderEncoding("UTF-8");
		factory.setSizeThreshold(memorySize);
		//File tmpDir = new File("c:/tmpdir"); // 当超过memorySize的时候，存到一个临时文件夹中
		//factory.setRepository(tmpDir);
		upload.setSizeMax(uploadSizeMax);

		JsArray ret = new JsArray();
		try {
			List<FileItem> items = upload.parseRequest(env.request);
			for (FileItem item : items) {
				if (! item.isFormField()) {
					String fileName = item.getName();
					if (fileName.length() == 0)
						continue;
					Object rv = handler.exec(item);
					ret.add(rv);
					if (rv.equals(false))
						break;
				}
			}
		} catch (FileUploadException e) {
			throw new MyException(E_PARAM, e.getMessage()); // TODO
		}
		return ret;
	}

	public Object api_upload() throws Exception
	{
		checkAuth(AUTH_LOGIN);
		//uid = _SESSION["uid"];
		String fmt = (String)param("fmt", "");
		String fileName = null;
		if (fmt.equals("raw") || fmt.equals("raw_b64"))
		{
			fileName = (String)mparam("f");
		}
		String type = (String)param("type", "default");
		boolean genThumb = (boolean)param("genThumb/b", false);
		boolean autoResize = (boolean)param("autoResize/b", true);
		String exif = (String)param("exif");
	
		if (!type.equals("user") && !type.equals("store") && !type.equals("default")) {
			throw new MyException(E_PARAM, "bad type: " + type);
		}
	
		int contentLen = env.request.getContentLength();
		JsArray ret = null;
		class HandleOneFile
		{
			// in?=null, 当文件没有扩展名时从in中读前几个字节猜测文件类型。必须可以in.reset()
			void apply(String fname, String type, String mimeType, Consumer<File> writeFile, JsObject ret, InputStream in) throws Exception
			{
				// 检查文件类型
				String ext = FilenameUtils.getExtension(fname).toLowerCase();
				String orgName = FilenameUtils.getName(fname);
				if (ext.length() == 0 && mimeType != null) {
					ext = indexOf(ALLOWED_MIME, (k,v)->v.equals(mimeType));
					if (ext == null) {
						if (in != null)
							ext = guessFileType(in);
						if (ext == null)
							throw new MyException(E_PARAM, String.format("MIME type not supported: `%s`", mimeType), String.format("文件类型`%s`不支持.", mimeType));
					}
				}
				
				if (ext.length() == 0 || !ALLOWED_MIME.containsKey(ext)) {
					throw new MyException(E_PARAM, String.format("unsupported extention name: `%s`", ext), String.format("文件扩展名`%s`不支持", ext));
				}
	
				String dir = type != null?
					"upload/" + type + "/" + date("yyyyMM", null) :
					"upload/" + date("yyyyMM", null);
				File dir1 = new File(env.baseDir + "/" + dir);
				if (! dir1.exists()) {
					if (! dir1.mkdirs())
						throw new MyException(E_SERVER, "fail to create folder: " + dir1.getCanonicalPath());
				}
				String fileName = null, thumbName = null;
				File mainFile = null, thumbFile = null;
				do {
					String base = String.valueOf(rand(100001,999999));
					fileName = String.format("%s/%s.%s", dir, base, ext);
					if (genThumb)
						thumbName = String.format("%s/t%s.%s", dir, base, ext);
					mainFile = new File(env.baseDir + "/" + fileName);
				} while(mainFile.exists());
	
	
				writeFile.accept(mainFile);
				
				if (autoResize && isPic(ext) && mainFile.length() > 500*KB) {
					resizeImage(mainFile, 1920, 1920, mainFile);
				}

				String sql = String.format("INSERT INTO Attachment (path, exif, tm, orgName) VALUES (%s, %s, now(), %s)",
						Q(fileName), Q(exif), Q(orgName));
				int id = execOne(sql, true);

				ret.put("id", id);
				ret.put("orgName", orgName);

				if (genThumb) {
					if (! UploadType.containsKey(type))	
						type = "default";
					PicSize info = UploadType.get(type);
					thumbFile = new File(env.baseDir + "/" + thumbName);
					resizeImage(mainFile, info.w, info.h, thumbFile);
					//file_put_contents(thumbName, "THUMB");
					sql = String.format("INSERT INTO Attachment (path, orgPicId, tm) VALUES (%s, %s, now())",
							Q(thumbName), id);
					int thumbId = execOne(sql, true);
					ret.put("thumbId", thumbId);
				}
			}
		}
		
		HandleOneFile handleOneFile = new HandleOneFile();
		if (fmt.equals("raw") || fmt.equals("raw_b64")) {
			JsObject ret1 = new JsObject();
			if (uploadSizeMax > 0 && contentLen > uploadSizeMax) {
				throw new MyException(E_PARAM, String.format("file is too large: contentLen(%s) > uploadSizeMax(%s)", contentLen, uploadSizeMax), "文件太大，禁止上传");
			}
			// 此类型会导致request.getInputStream()读不到数据。(因为此前已被request.getParameter使用过stream)
			if (env.request.getContentType().toLowerCase().contains("application/x-www-form-urlencoded"))
				throw new MyException(E_PARAM, "Content-Type 'application/x-www-form-urlencoded' is forbidden for upload.", "编码类型错误");

			handleOneFile.apply(fileName, type, null, f -> {
				// for upload raw/raw_b64
				try {
					InputStream in = fmt.equals("raw_b64")?
						Base64.getDecoder().wrap(env.request.getInputStream()) :
						env.request.getInputStream();
					writeFile(in, f);
				} catch (IOException e) {
					throw new MyException(E_PARAM, e.getMessage());
				}
			}, ret1, null);
			ret = new JsArray(ret1);
		}
		else {
			DiskFileItemFactory factory = new DiskFileItemFactory();
			ServletFileUpload upload = new ServletFileUpload(factory);
			//upload.setHeaderEncoding("UTF-8");
			factory.setSizeThreshold(memorySize);
			//File tmpDir = new File("c:/tmpdir"); // 当超过memorySize的时候，存到一个临时文件夹中
			//factory.setRepository(tmpDir);
			upload.setSizeMax(uploadSizeMax);
	
			try {
				List<FileItem> items = upload.parseRequest(env.request);
				ret = new JsArray();
				for (FileItem item : items) {
					if (! item.isFormField()) {
						fileName = item.getName();
						if (fileName.length() == 0)
							continue;
						JsObject ret1 = new JsObject();
						handleOneFile.apply(fileName, type, item.getContentType(), f -> { 
							try {
								item.write(f);
							} catch (Exception e) {
								throw new MyException(E_PARAM, e.getMessage());
							} 
						}, ret1, item.getInputStream() );
						ret.add(ret1);
					}
				}
	
			} catch (FileUploadException e) {
				throw new MyException(E_PARAM, e.getMessage()); // TODO
			}
		}
		if (ret == null || ret.size() == 0) {
			throw new MyException(E_PARAM, "no file uploaded. upload size=" + contentLen, "没有文件上传或文件过大。");
		}
	
		return ret;
	}
	
	// NOTE: this function does not conform to the interface standard, it return file data directly or HTTP 404 error
	// please use "exit" instead of "return"
	public Object api_att() throws Exception
	{
		// overwritten the default
		header("Cache-Control", "private, max-age=99999999");
	
		//checkAuth(AUTH_LOGIN);
		Integer id = (Integer)param("id");
		Integer thumbId = (Integer)param("thumbId");
	
		if ((id == null || id <= 0) && (thumbId == null || thumbId <= 0))
		{
			env.response.setStatus(404);
			exit();
		}
		// setup cache via etag.
		String etag = null;
		if (thumbId == null) {
			etag = "att-" + id;
		}
		else {
			etag = "att-t" + thumbId;
		}
		String etag1 = this.env.request.getHeader("if-none-match");
		if (etag.equals(etag1)) {
			env.response.setStatus(304);
			exit();
		}
		String sql = null;
		if (id != null)
			sql = "SELECT path, orgName FROM Attachment WHERE id=" + id;
		else {
			// t0: original, a2: thumb
			sql = "SELECT t0.path, t0.orgName FROM Attachment t0 INNER JOIN Attachment a2 ON t0.id=a2.orgPicId WHERE a2.id=" + thumbId;
		}
		Object rv = queryOne(sql);
		if (rv.equals(false))
		{
			env.response.setStatus(404);
			exit();
		}
		JsArray rv1 = (JsArray)rv;
		String file = (String)rv1.get(0);
		String orgName = (String)rv1.get(1);
		if (regexMatch(file, "https?:").find()) {
			env.response.sendRedirect(file);
			exit();
		}
	
		String fileAbs = env.baseDir + "/" + file;
		if (! new File(fileAbs).exists()) {
			env.response.setStatus(404);
			exit();
		}
	
		// 对指定mime的直接返回，否则使用重定向。
		// TODO: 使用 apache x-sendfile module 解决性能和安全性问题。
		String ext = FilenameUtils.getExtension(file);
		//mimeType = ALLOWED_MIME[ext] ?: "application/octet-stream";
		String mimeType = ALLOWED_MIME.getOrDefault(ext, null);
		if (mimeType != null) {
			header("Content-Type", mimeType);
			header("Etag", etag);
			//header("Expires: Thu, 3 Sep 2020 08:52:00 GMT");
			//header("Content-length: " . filesize(file));
			if (orgName == null)
				orgName = FilenameUtils.getName(file);
			String disp = null;
			if (isPic(ext)) {
				disp = "filename=" + java.net.URLEncoder.encode(orgName, "UTF-8");
			}
			else {
				disp = "attachment; filename=" + java.net.URLEncoder.encode(orgName, "UTF-8");
			}
			header("Content-Disposition", disp);
			writeFile(fileAbs, env.response.getOutputStream());
		}
		else {
			String baseUrl = getBaseUrl(false);
			String url = baseUrl + file;
			env.response.sendRedirect(url); // TODO
		}
		exit();
		return null;
	}
}
