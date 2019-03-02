package service;

import java.io.*;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;



@Service
public class FileStorageImpl implements FileStorage {

    Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private final Path rootLocation = Paths.get("filestorage");
    private static final Path rootLocationZip = Paths.get("filestorageZip");

    @Override
    public String store(MultipartFile file){

        try {
            if (!String.valueOf(rootLocation.resolve(file.getOriginalFilename())).endsWith(".zip"))//filters only zip
                return "! the file is not a zip";

            Files.copy(file.getInputStream(), this.rootLocation.resolve(file.getOriginalFilename()), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            return " Error copy file : "+e.getMessage();
        }

        String pathZipIntoFile = String.valueOf(rootLocation.resolve(file.getOriginalFilename()));
        String nameFolder = pathZipIntoFile.replace(".zip","");
        try {
            unpackZipFile(pathZipIntoFile,nameFolder);
        }catch (Exception e){
            return " Error zip unpack file : "+e.getMessage();
        }

        try {
            return processFilesFolder(nameFolder,rootLocationZip);
        }catch (Exception e){
            return " Error process files folder : "+e.getMessage();
        }
    }

    @Override
    public Resource loadFile(String filename) {
        try {
            Path file = rootLocationZip.resolve(filename);
            Resource resource = new UrlResource(file.toUri());
            if(resource.exists() || resource.isReadable()) {
                return resource;
            }else{
                throw new RuntimeException("FAIL!");
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error! -> message = " + e.getMessage());
        }
    }

    @Override
    public void deleteAll() {
        FileSystemUtils.deleteRecursively(rootLocation.toFile());
        FileSystemUtils.deleteRecursively(rootLocationZip.toFile());
    }

    @Override
    public void init() {
        try {
            Files.createDirectory(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize storage!");
        }
    }

    @Override
    public Stream<Path> loadFiles() {
        try {
            return Files.walk(this.rootLocationZip, 1)
                    .filter(path -> !path.equals(this.rootLocationZip))
                    .map(this.rootLocationZip::relativize);
        }
        catch (IOException e) {
            throw new RuntimeException("\"Failed to read stored file");
        }
    }

    public void unpackZipFile(String pathZipIntoFile, String nameFolder){

            File myFile = new File(pathZipIntoFile);
            Path pathFolder = Paths.get(nameFolder);

            try (ZipFile zip = new ZipFile(myFile.getPath())) {
                createDirectoryIf(pathFolder);
                Enumeration entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    String filePath = entry.getName();
                    if (entry.isDirectory()) {
                        new File(nameFolder+"/"+filePath).mkdirs();
                        log.info("unpackZip Directory: "+nameFolder+"/"+filePath+" - "+Files.exists(Paths.get(nameFolder+"/"+filePath)));
                    } else {
                        try(BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(new File(nameFolder+"/"+filePath)));
                            InputStream inputStream = zip.getInputStream(entry)) {
                            write(inputStream, bufferedOutputStream);
                            log.info("unpackZip file: "+nameFolder+"/"+filePath+" - "+Files.exists(Paths.get(nameFolder+"/"+filePath)));
                        }
                        catch (Exception e){
                            log.info("unpackZip: "+e.getMessage());
                        }
                    }
                }
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
    }

    public String processFilesFolder(String foldr,Path LocationZip) throws IOException {

            File js = getFileFromFolder(foldr,".js");
            File html = getFileFromFolder(foldr,".html");

            html = jsEmbeddingInHtml(js,html,LocationZip);

            Map<String, String> replaceMap = searchForReplacementBlocks(html,foldr);

            return processingHtmlFileByBlocks(html,foldr,replaceMap,rootLocationZip);
    }

    private String getNameFile(Path pathFolder, File file, String replaceString){

        String name;
        if (!file.getName().endsWith("DFP.html"))
            name =  file.getName().replace(".html","DFP.html");
        else
            name =  file.getName();

        return rootLocationZip+"/"+name;
    }

    private static void write(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) >= 0)
            out.write(buffer, 0, len);

        out.close();
        in.close();
    }

    private String convertPictureFromLineToBase64(String replaceKey,String lstIntoPars) {

        String pathPicture = lstIntoPars+"/"+replaceKey;
        log.info("dirPicture: "+pathPicture);

        try {
            File picture = new File(pathPicture);
            log.info("picture: "+picture.getPath());
            int andPictureint = picture.getPath().indexOf(".");
            String andPicture = picture.getPath().substring(andPictureint+1);
            String imgstr = encodeFileToBase64Binary(picture);
            log.info("picture: Base64 null - "+(imgstr=="null"));
            picture = null;
            return "{type:createjs.AbstractLoader.IMAGE, src:\"data:image/"+andPicture+";base64,"+imgstr;

        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return "";
    }

    private String encodeFileToBase64Binary(File file){
        String encodedfile = null;
        log.info("picture in encode1: "+file.getPath());
        log.info("picture in encode length 1: "+file.length());
        try (FileInputStream fileInputStreamReader = new FileInputStream(file)) {
            log.info("picture in encode2: "+file.getPath());
            byte[] bytes = new byte[(int)file.length()];
            fileInputStreamReader.read(bytes);
            encodedfile = new String(Base64.encodeBase64(bytes), "UTF-8");
            log.info("encodedfile null: "+(encodedfile=="null"));
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            log.error(e.getMessage());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            log.error(e.getMessage());
        }

        return encodedfile;
    }

    private void deleteToList(String strintPath){

            FileSystemUtils.deleteRecursively(Paths.get(strintPath).toFile());

    }

    private File jsEmbeddingInHtml(File js,File html,Path LocationZip) throws IOException {

        if(!(js ==null)){
            log.info("js ->: "+js.length());

                String replasJsString = js.getName();
                String toReplasJsString = FileUtils.readFileToString(js);

                String htmlString = FileUtils.readFileToString(html);
                //into js to html
                htmlString = htmlString.replace("src=\""+replasJsString+"\">", ">"+toReplasJsString);

                String nameFinalFile = getNameFile(LocationZip,html,"DFP");
                File newHtmlFile = new File(nameFinalFile);
                FileUtils.writeStringToFile(newHtmlFile, htmlString);
                return newHtmlFile;
            }


        return html;
    }

    private Map<String, String> searchForReplacementBlocks(File html,String foldr){

        Map<String, String> replacMap = new HashMap<>();

        try(BufferedReader bufTextFile = new BufferedReader(new FileReader(html))) {

            boolean bodyTagOopen = false;
            boolean scriptTagOopen = false;

            while (bufTextFile.ready()) {

                String lineFile = bufTextFile.readLine();

                //check on the picture
                int startingIndex = lineFile.indexOf("{src:");
                int finalIndex = lineFile.indexOf("\", id:\"");
                if ((startingIndex != -1) && (finalIndex != -1)) {
                    String replaceKey = lineFile.substring(startingIndex, finalIndex);
                    String replaceValue = convertPictureFromLineToBase64(replaceKey.replace("{src:\"", ""), foldr);

                    if (replaceValue == "null") {
                        log.error("replaceValue: null");
                    }
                    replacMap.put(replaceKey, replaceValue);
                }

                //check on body
                if (!bodyTagOopen && lineFile.indexOf("<body") != -1) {
                    bodyTagOopen = true;
                    continue;
                }
                if (bodyTagOopen && lineFile.indexOf("<a href") != -1) {
                    replacMap.put(lineFile, "<a href=\"%%CLICK_URL_UNESC%%http://ngs.ru\" target=\"_blank\">");
                    bodyTagOopen = false;
                } else if (bodyTagOopen) {
                    bodyTagOopen = false;
                }

                //check script
                if (!scriptTagOopen && lineFile.indexOf("script") != -1) {
                    scriptTagOopen = true;
                    continue;
                }
                if (scriptTagOopen && lineFile.indexOf("var") != -1) {
                    replacMap.put(lineFile, "");
                    scriptTagOopen = false;
                } else if (scriptTagOopen) {
                    scriptTagOopen = false;
                }

            }
        }
        catch (Exception ex){
            System.out.println(ex.getMessage());
        }

        return replacMap;
    }

    private String processingHtmlFileByBlocks(File html,String foldr, Map<String, String> replaceMap,Path LocationZip){

        if (!(html ==null)){
            log.info("html ->: "+html.length());
            try {
                String htmlString = FileUtils.readFileToString(html);

                for (Map.Entry<String, String> par : replaceMap.entrySet()) {
                    htmlString = htmlString.replace(par.getKey(), par.getValue());
                }
                String nameFinalFile = getNameFile(LocationZip,html,"DFP");
                File newHtmlFile = new File(nameFinalFile);
                FileUtils.writeStringToFile(newHtmlFile, htmlString);

                deleteToList(foldr);

                if(newHtmlFile.getName()=="")
                    return "error 500";

                return newHtmlFile.getName();
            }
            catch (Exception ex){
                System.out.println(ex.getMessage());
            }
        }

        deleteToList(foldr);
        return "html file is null.";
    }

    public File getFileFromFolder(String foldr,String endsWith){

        File dirIntoPars = new File(foldr+"/");
        File[] arrFilesIntoPars = dirIntoPars.listFiles();

        return Arrays.stream(arrFilesIntoPars)
                .filter(file -> file.getName().endsWith(endsWith))
                .toArray(File[]::new)[0];
    }

    private void createDirectoryIf(Path pathFolder) throws IOException {
        if (!(new File(pathFolder.toUri()).exists()))
            Files.createDirectory(pathFolder);
    }
}
