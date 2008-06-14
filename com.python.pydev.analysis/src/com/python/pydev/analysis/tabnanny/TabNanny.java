package com.python.pydev.analysis.tabnanny;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.python.pydev.core.Tuple3;
import org.python.pydev.core.docutils.PySelection;
import org.python.pydev.core.structure.FastStringBuffer;
import org.python.pydev.editor.autoedit.IIndentPrefs;
import org.python.pydev.plugin.PydevPlugin;

import com.python.pydev.analysis.IAnalysisPreferences;
import com.python.pydev.analysis.messages.IMessage;
import com.python.pydev.analysis.messages.Message;

/**
 * Name was gotten from the python library... but the implementation is very different ;-)
 * 
 * @author Fabio
 */
public class TabNanny{


    /**
     * Analyze the doc for mixed tabs and indents with the wrong number of chars.
     * 
     * @return a list with the error messages to be shown to the user.
     */
    public static List<IMessage> analyzeDoc(IDocument doc, IAnalysisPreferences analysisPrefs, String moduleName, IIndentPrefs indentPrefs) {
        ArrayList<IMessage> ret = new ArrayList<IMessage>();
        
        //don't even try to gather indentation errors if they should be ignored.
        if(analysisPrefs.getSeverityForType(IAnalysisPreferences.TYPE_INDENTATION_PROBLEM) == IMarker.SEVERITY_INFO){
            return ret;
        }
        
        List<Tuple3<String, Integer, Boolean>> foundTabs = new ArrayList<Tuple3<String, Integer, Boolean>>();
        List<Tuple3<String, Integer, Boolean>> foundSpaces = new ArrayList<Tuple3<String, Integer, Boolean>>();
        
        TabNannyDocIterator it = new TabNannyDocIterator(doc);
        while(it.hasNext()){
            Tuple3<String, Integer, Boolean> indentation = it.next();
            //it can actually be in both (if we have spaces and tabs in the same indent line).
            if(indentation.o1.indexOf('\t') != -1){
                foundTabs.add(indentation);
            }
            if(indentation.o1.indexOf(' ') != -1){ 
                foundSpaces.add(indentation);
            }
        }
        
        int spacesFoundSize = foundSpaces.size();
        int tabsFoundSize = foundTabs.size();
        if(spacesFoundSize == 0 && tabsFoundSize == 0){
            //nothing to do here... (no indents available)
            return ret;
        }
        
        //let's discover whether we should mark the tabs found as errors or the spaces found...
        boolean markTabsAsError;
        
        //if we found the same number of indents for tabs and spaces, let's use the user-prefs to decide what to do
        if(spacesFoundSize == tabsFoundSize){
            //ok, we have both, spaces and tabs... let's see what the user actually wants
            markTabsAsError = indentPrefs.getUseSpaces();
            
        }else if(tabsFoundSize > spacesFoundSize){
            //let's see what appears more in the file (and mark the other as error).
            markTabsAsError = false;
            
        }else{
            markTabsAsError = true;
            
        }
        
        List<Tuple3<String, Integer, Boolean>> errorsAre;
        List<Tuple3<String, Integer, Boolean>> validsAre;
        String errorMsg; 
        char errorChar;
        
        if(markTabsAsError){
            validsAre = foundSpaces;
            errorsAre = foundTabs;
            errorMsg = "Mixed Indentation: Tab found"; 
            errorChar = '\t';
            
            createBadIndentForSpacesMessages(doc, analysisPrefs, indentPrefs, ret, validsAre);
            
        }else{
            validsAre = foundTabs;
            errorsAre = foundSpaces;
            errorMsg = "Mixed Indentation: Spaces found"; 
            errorChar = ' ';
        }

        createMixedErrorMessages(doc, analysisPrefs, ret, errorsAre, errorMsg, errorChar);
        return ret;
    }

    /**
     * Creates the errors that are related to a bad indentation (number of space chars is not ok).
     */
    private static void createBadIndentForSpacesMessages(IDocument doc, IAnalysisPreferences analysisPrefs, IIndentPrefs indentPrefs,
            ArrayList<IMessage> ret, List<Tuple3<String, Integer, Boolean>> validsAre) {
        
        int tabWidth = indentPrefs.getTabWidth();
        //if we're analyzing the spaces, let's mark invalid indents (tabs are not searched for those because
        //a tab always marks a full indent).
        
        FastStringBuffer buffer = new FastStringBuffer();
        for(Tuple3<String, Integer, Boolean> indentation:validsAre){
            
            if(!indentation.o3){ //if it does not have more contents (its only whitespaces), let's keep on going!
                continue;
            }
            String indentStr = indentation.o1;
            if(indentStr.indexOf("\t") != -1){
                continue; //the ones that appear in tabs and spaces should not be analyzed here (they'll have their own error messages).
            }
            
            int lenFound = indentStr.length();
            int extraChars = lenFound%tabWidth;
            if(extraChars != 0){
                
                Integer offset = indentation.o2;
                int startLine = PySelection.getLineOfOffset(doc, offset)+1;
                int startCol = 1;
                int endCol = startCol+lenFound;

                buffer.clear();
                ret.add(new Message(IAnalysisPreferences.TYPE_INDENTATION_PROBLEM, 
                        buffer.append("Bad Indentation (").append(lenFound).append(" spaces)").toString(), 
                        startLine, startLine, startCol, endCol, analysisPrefs));
                    
            }
        }
    }

    /**
     * Creates the errors that are related to the mixed indentation.
     */
    private static void createMixedErrorMessages(IDocument doc, IAnalysisPreferences analysisPrefs, ArrayList<IMessage> ret,
            List<Tuple3<String, Integer, Boolean>> errorsAre, String errorMsg, char errorChar) {
        
        for(Tuple3<String, Integer, Boolean> indentation:errorsAre){
            Integer offset = indentation.o2;
            int startLine = PySelection.getLineOfOffset(doc, offset)+1;
            IRegion region;
            try {
                region = doc.getLineInformationOfOffset(offset);
                int startCol = offset - region.getOffset()+1;
                String indentationString = indentation.o1;
                int charIndex = indentationString.indexOf(errorChar);
                startCol += charIndex;
                
                //now, get the endCol
                int endCol = startCol;
                int indentationStringLen = indentationString.length();
                
                //endCol starts at 1, but string access starts at 0 (so <= is needed)
                while(endCol <= indentationStringLen){
                    if(indentationString.charAt(endCol-1) == errorChar){
                        endCol++;
                    }else{
                        break;
                    }
                }
                
                ret.add(new Message(IAnalysisPreferences.TYPE_INDENTATION_PROBLEM, errorMsg, 
                        startLine, startLine, startCol, endCol, analysisPrefs));
                
            } catch (BadLocationException e) {
                PydevPlugin.log(e);
            }
            
        }
    }
    
}