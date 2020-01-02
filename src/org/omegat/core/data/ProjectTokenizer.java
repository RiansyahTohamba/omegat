package org.omegat.core.data;

import org.omegat.CLIParameters;
import org.omegat.core.Core;
import org.omegat.tokenizer.DefaultTokenizer;
import org.omegat.tokenizer.ITokenizer;
import org.omegat.util.Log;
import org.omegat.util.StringUtil;

public class ProjectTokenizer {

    public ITokenizer getTarget(ProjectProperties props) {
        ITokenizer targetTokenizer = createTokenizer(Core.getParams().get(CLIParameters.TOKENIZER_TARGET),
                props.getTargetTokenizer());
        Log.log("Target tokenizer: " + targetTokenizer.getClass().getName());
        return targetTokenizer;
    }

    public ITokenizer getSource(ProjectProperties props) {
        ITokenizer sourceTokenizer = createTokenizer(Core.getParams().get(CLIParameters.TOKENIZER_SOURCE),
                props.getSourceTokenizer());
        Log.log("Source tokenizer: " + sourceTokenizer.getClass().getName());
        return sourceTokenizer;
    }
    /**
     * Create tokenizer class. Classes are prioritized:
     * <ol><li>Class specified on command line via <code>--ITokenizer</code>
     * and <code>--ITokenizerTarget</code></li>
     * <li>Class specified in project settings</li>
     * <li>{@link DefaultTokenizer}</li>
     * </ol>
     *
     * @param cmdLine Tokenizer class specified on command line
     * @return Tokenizer implementation
     */
    protected ITokenizer createTokenizer(String cmdLine, Class<?> projectPref) {
        if (!StringUtil.isEmpty(cmdLine)) {
            try {
                return (ITokenizer) this.getClass().getClassLoader().loadClass(cmdLine).getDeclaredConstructor()
                        .newInstance();
            } catch (ClassNotFoundException e) {
                Log.log(e.toString());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
        try {
            return (ITokenizer) projectPref.getDeclaredConstructor().newInstance();
        } catch (Throwable e) {
            Log.log(e);
        }

        return new DefaultTokenizer();
    }
}