package org.jdownloader.plugins.components;

//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.appwork.utils.DebugMode;
import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.URLEncode;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.keycaptcha.KeyCaptcha;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter.VideoExtensions;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.Form.MethodType;
import jd.parser.html.HTMLParser;
import jd.parser.html.InputField;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountRequiredException;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.components.SiteType.SiteTemplate;
import jd.plugins.hoster.RTMPDownload;

public class XFileSharingProBasic extends antiDDoSForHost {
    public XFileSharingProBasic(PluginWrapper wrapper) {
        super(wrapper);
        // this.enablePremium(super.getPremiumLink());
    }

    // private static String[] domains = new String[] { "xvideosharing.com" };
    //
    // public static String[] getAnnotationNames() {
    // return new String[] { domains[0] };
    // }
    //
    // @Override
    // public String[] siteSupportedNames() {
    // return domains;
    // }
    //
    // /**
    // * returns the annotation pattern array: 'https?://(?:www\\.)?(?:domain1|domain2)/(?:embed\\-)?[a-z0-9]{12}'
    // *
    // */
    // public static String[] getAnnotationUrls() {
    // // construct pattern
    // final String host = getHostsPattern();
    // return new String[] { host + "/(?:embed\\-)?[a-z0-9]{12}(?:/[^/]+\\.html)?" };
    // }
    //
    // /** returns 'https?://(?:www\\.)?(?:domain1|domain2)' */
    // private static String getHostsPattern() {
    // final String hosts = "https?://(?:www\\.)?" + "(?:" + getHostsPatternPart() + ")";
    // return hosts;
    // }
    //
    // /** Returns '(?:domain1|domain2)' */
    // public static String getHostsPatternPart() {
    // final StringBuilder pattern = new StringBuilder();
    // for (final String name : domains) {
    // pattern.append((pattern.length() > 0 ? "|" : "") + Pattern.quote(name));
    // }
    // return pattern.toString();
    // }
    /* Used variables */
    public String                correctedBR                  = "";
    protected String             fuid                         = null;
    /*
     * Note:Final value will be set later in init(). CAN NOT be negative or zero! (ie. -1 or 0) Otherwise math sections fail. .:. use [1-20]
     */
    private static AtomicInteger totalMaxSimultanFreeDownload = new AtomicInteger(1);
    /* don't touch the following! */
    private static AtomicInteger maxFree                      = new AtomicInteger(1);

    /**
     * DEV NOTES XfileSharingProBasic Version 4.0.1.7<br />
     ****************************
     * NOTES from raztoki <br/>
     * - no need to set setfollowredirect true. <br />
     * - maintain the primary domain base url (protocol://subdomain.domain.tld.cctld), everything else will be based off that! do not fubar
     * with standard browser behaviours.
     ****************************
     * mods: See overridden functions<br />
     * limit-info:<br />
     * captchatype-info: null 4dignum solvemedia reCaptchaV2<br />
     * Last compatible XFileSharingProBasic template: Version 2.7.8.7 in revision 40351 other:<br />
     */
    @Override
    public void init() {
        /* Errorhandling as we should not set negative values her!! */
        if (getMaxSimultaneousFreeAnonymousDownloads() < 0) {
            totalMaxSimultanFreeDownload.set(20);
        } else {
            totalMaxSimultanFreeDownload.set(getMaxSimultaneousFreeAnonymousDownloads());
        }
    }

    @Override
    public String getAGBLink() {
        return this.getMainPage() + "/tos.html";
    }

    public String getPurchasePremiumURL() {
        return this.getMainPage() + "/premium.html";
    }

    /**
     * Returns whether resume is supported or not for current download mode based on account availability and account type. <br />
     * Override this function to set resume settings!
     */
    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return true;
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return true;
        } else {
            /* Free(anonymous) and unknown account type */
            return false;
        }
    }

    /**
     * Returns how many max. chunks per file are allowed for current download mode based on account availability and account type. <br />
     * Override this function to set chunks settings!
     */
    public int getMaxChunks(final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return 1;
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 1;
        }
    }

    // /** Returns max. simultaneous downloads for current download mode based on account availibility and account type. */
    // private int getDownloadModeMaxSimultaneousDownloads(final Account account) {
    // if (account.getType() == AccountType.FREE) {
    // /* Free Account */
    // return getMaxSimultaneousFreeAccountDownloads();
    // } else if (account.getType() == AccountType.PREMIUM) {
    // /* Premium account */
    // return getMaxSimultanPremiumDownloadNum();
    // } else {
    // /* Free(anonymous) and unknown account type */
    // return getMaxSimultaneousFreeAnonymousDownloads();
    // }
    // }
    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return maxFree.get();
    }

    public int getMaxSimultaneousFreeAnonymousDownloads() {
        return 1;
    }

    public int getMaxSimultaneousFreeAccountDownloads() {
        return 1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    /** Returns direct-link-property-String for current download mode based on account availibility and account type. */
    protected static String getDownloadModeDirectlinkProperty(final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return "freelink2";
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return "premlink";
        } else {
            /* Free(anonymous) and unknown account type */
            return "freelink";
        }
    }

    /**
     * @return true: Website supports https and plugin will prefer https. <br />
     *         false: Website does not support https - plugin will avoid https. <br />
     *         default: true
     */
    public boolean supports_https() {
        return true;
    }

    /**
     * Relevant for accounts.
     *
     * @return true: Try to find more exact (down to the second instead of day) expire date via '/?op=payments'. <br />
     *         false: For premium accounts: Do NOT try to find more exact expire date via '?op=payments'. Rely on given date string
     *         (yyyy-MM-dd) which is less precise. <br />
     *         default: true
     */
    public boolean supports_precise_expire_date() {
        return true;
    }

    /**
     * <b> Enabling this may lead to at least one additional website-request! </b>
     *
     * @return true: Implies that the hoster only allows audio-content to be uploaded. Enabling this will make plugin try to find
     *         audio-downloadlinks via '/mp3embed-<fuid>'. Also sets mime-hint via CompiledFiletypeFilter.ImageExtensions.JPG. <br />
     *         false: Website is just an usual filehost, use given fileextension if possible. <br />
     *         default: false
     */
    public boolean isAudiohoster() {
        return false;
    }

    /**
     * 2019-05-16: TODO: Consider removing this or automatically return true if isVideohoster_2 is true. 99% of XFS hosts do not support
     * this anymore! <b> Enabling this may lead to at least one additional website-request! </b> <br />
     * Enable this for websites using <a href="https://sibsoft.net/xvideosharing.html">XVideosharing</a>. <br />
     * Demo-Website: <a href="http://xvideosharing.com">xvideosharing.com</a>
     *
     * @return true: Try to find final downloadlink via '/vidembed-<fuid>' request. <br />
     *         false: Skips this part. <br />
     *         default: false
     */
    public boolean isVideohoster() {
        return false;
    }

    /**
     * <b> Enabling this may lead to at least one additional website-request! </b> <br />
     * Enable this for websites using <a href="https://sibsoft.net/xvideosharing.html">XVideosharing</a>. <br />
     * Demo-Website: <a href="http://xvideosharing.com">xvideosharing.com</a>
     *
     * @return true: Try to find final downloadlink via '/embed-<fuid>.html' request. <br />
     *         false: Skips this part. <br />
     *         default: false
     */
    public boolean isVideohoster_2() {
        return false;
    }

    /**
     * Enable this for websites using <a href="https://sibsoft.net/xvideosharing.html">XVideosharing</a>. <br />
     * Demo-Website: <a href="http://xvideosharing.com">xvideosharing.com</a>
     *
     * @return true: Implies that the hoster only allows video-content to be uploaded. Enforces .mp4 extension for all URLs. Also set
     *         mime-hint via CompiledFiletypeFilter.VideoExtensions.MP4. <br />
     *         false: Website is just an usual filehost, use given fileextension if possible. <br />
     *         default: false
     */
    public boolean isVideohoster_enforce_video_filename() {
        return false;
    }

    /**
     * Enable this for websites using <a href="https://sibsoft.net/ximagesharing.html">XImagesharing</a>. <br />
     * Demo-Website: <a href="http://ximagesharing.com">ximagesharing.com</a>
     *
     * @return true: Implies that the hoster only allows photo-content to be uploaded. Enabling this will make plugin try to find
     *         picture-downloadlinks. Also sets mime-hint via CompiledFiletypeFilter.ImageExtensions.JPG. <br />
     *         false: Website is just an usual filehost, use given fileextension if possible. <br />
     *         default: false
     */
    public boolean isImagehoster() {
        return false;
    }

    /**
     * See also function getFilesizeViaAvailablecheckAlt! <br />
     * <b> Enabling this will eventually lead to at least one additional website-request! </b>
     *
     * @return true: Implies that website supports getFilesizeViaAvailablecheckAlt call as an alternative source for filesize-parsing.
     *         <br />
     *         false: Implies that website does NOT support getFilesizeViaAvailablecheckAlt. <br />
     *         default: true
     */
    public boolean supports_availablecheck_alt() {
        return true;
    }

    /**
     * Only works when getFilesizeViaAvailablecheckAlt returns true! See getFilesizeViaAvailablecheckAlt!
     *
     * @return true: Implies that website supports getFilesizeViaAvailablecheckAlt call without Form-handling (one call less than usual) as
     *         an alternative source for filesize-parsing. <br />
     *         false: Implies that website does NOT support getFilesizeViaAvailablecheckAlt without Form-handling. <br />
     *         default: true
     */
    public boolean supports_availablecheck_filesize_alt_fast() {
        return true;
    }

    /**
     * See also function getFilesizeViaAvailablecheckAlt!
     *
     * @return true: Website uses old version of getFilesizeViaAvailablecheckAlt. Old will be tried first, then new if it fails. <br />
     *         false: Website uses current version of getFilesizeViaAvailablecheckAlt - it will be used first and if it fails, old call will
     *         be tried. <br />
     *         default: false
     */
    public boolean prefer_availablecheck_filesize_alt_type_old() {
        return false;
    }

    /**
     * See also function getFnameViaAbuseLink!<br />
     * <b> Enabling this will eventually lead to at least one additional website-request! </b>
     *
     * @return true: Implies that website supports getFnameViaAbuseLink call as an alternative source for filename-parsing. <br />
     *         false: Implies that website does NOT support getFnameViaAbuseLink. <br />
     *         default: true
     */
    public boolean supports_availablecheck_filename_abuse() {
        return true;
    }

    /**
     * @return true: Try to RegEx filesize from normal html code. If this fails due to static texts on a website or even fake information,
     *         all links of a filehost may just get displayed with the same/wrong filesize. <br />
     *         false: Do not RegEx filesize from normal html code. Plugin will still be able to find filesize if supports_availablecheck_alt
     *         or supports_availablecheck_alt_fast is enabled (=default)! <br />
     *         default: true
     */
    public boolean supports_availablecheck_filesize_html() {
        return true;
    }

    /**
     * This is designed to find the filesize during availablecheck for videohosts - videohosts usually don't display the filesize anywhere!
     * <br />
     * CAUTION: Only set this to true if a filehost: <br />
     * 1. Allows users to embed videos via '/embed-<fuid>.html'. <br />
     * 2. Does not display a filesize anywhere inside html code or other calls where we do not have to do an http request on a directurl.
     * <br />
     * 3. Allows a lot of simultaneous connections. <br />
     * 4. Is FAST - if it is not fast, this will noticably slow down the linkchecking procedure!
     *
     * @return true: requestFileInformation will use '/embed' to do an additional offline-check and find the filesize. <br />
     *         false: Disable this.<br />
     *         default: false
     */
    public boolean supports_availablecheck_filesize_via_videohoster_2_directurl() {
        return false;
    }

    /**
     * A correct setting increases linkcheck-speed as unnecessary redirects will be avoided. <br />
     * Also in some cases, you may get 404 errors or redirects to other websites if this setting is not correct.
     *
     * @return true: Implies that website requires 'www.' in all URLs. <br />
     *         false: Implies that website does NOT require 'www.' in all URLs. <br />
     *         default: false
     */
    public boolean requires_WWW() {
        return false;
    }

    /**
     * <b>Reduces the chances of fatal plugin failure due to lack of filename!</b> <br />
     * <b> Keep in mind: If isImagehoster() is enabled, it has its own fallback for missing filenames as imagehosts often don't show
     * filenames until we start the download. </b>
     *
     * @return true: Fallback to filename from contentURL or fuid as filename if no filename is found. <br />
     *         false: Throw LinkStatus.ERROR_PLUGIN_DEFECT on missing filename. <br />
     *         default: true
     */
    public boolean allow_fallback_filename_on_parser_failure() {
        return true;
    }

    /**
     * @return: Skip pre-download waittime or not. See waitTime function below. <br />
     *          default: false <br />
     *          example true: uploadrar.com
     */
    public boolean preDownloadWaittimeSkippable() {
        return false;
    }

    /**
     * TODO: 2019-05-09: Consider removing this as it has not been used in a single plugin! <br />
     *
     * @return true: Wait a forced amount of pre-download-waittime even if no waittime is found in html code. Code will make sure that the
     *         found waittime is between waitsecondsmin and waitsecondsmax. <br />
     *         If that is NOT the case, waitsecondsforced will be used as pre-download-waittime instead. <br />
     *         false: Only wait pre-download-waittime if waittime is found in html. <br />
     *         default: false
     */
    protected boolean forcePreDownloadWaittime() {
        return false;
    }

    /**
     * <b>This only gets used if isWaitforced is true!</b>
     *
     * @return Minimum seconds of pre-download-waittime. <br />
     *         default: 3
     */
    public int getWaitsecondsmin() {
        return 3;
    }

    /**
     * <b>This only gets used if isWaitforced is true!</b>
     *
     * @return Maximum seconds of pre-download-waittime. <br />
     *         default: 100
     */
    public int getWaitsecondsmax() {
        return 100;
    }

    /**
     * <b>Fallback-waittime-value! This only gets used if waitforced is true!</b>
     *
     * @return Hardcoded value of seconds to wait if no waittime is found or it is not within waitsecondsmin and waitsecondsmax. <br />
     *         default: 5
     */
    public int getWaitsecondsforced() {
        return 5;
    }

    @Override
    public void correctDownloadLink(final DownloadLink link) {
        final String fuid = this.fuid != null ? this.fuid : getFUIDFromURL(link);
        if (fuid != null) {
            /* link cleanup, prefer https if possible */
            if (link.getPluginPatternMatcher().matches("https?://[A-Za-z0-9\\-\\.]+/embed\\-[a-z0-9]{12}")) {
                link.setContentUrl(getMainPage() + "/embed-" + fuid + ".html");
            }
            link.setPluginPatternMatcher(getMainPage() + "/" + fuid);
            link.setLinkID(getHost() + "://" + fuid);
        }
    }

    @Override
    public Browser prepBrowser(final Browser prepBr, final String host) {
        if (!(this.browserPrepped.containsKey(prepBr) && this.browserPrepped.get(prepBr) == Boolean.TRUE)) {
            super.prepBrowser(prepBr, host);
            /* define custom browser headers and language settings */
            prepBr.setCookie(getMainPage(), "lang", "english");
            prepBr.setAllowedResponseCodes(new int[] { 500 });
        }
        return prepBr;
    }

    /** Returns https?://host.tld */
    protected String getMainPage() {
        final String[] hosts = this.siteSupportedNames();
        String mainpage;
        final String protocol;
        if (this.supports_https()) {
            protocol = "https://";
        } else {
            protocol = "http://";
        }
        mainpage = protocol;
        if (requires_WWW()) {
            mainpage += "www.";
        }
        mainpage += hosts[0];
        return mainpage;
    }

    /**
     * @return true: Link is password protected <br />
     *         false: Link is not password protected
     */
    public boolean isPasswordProtected() {
        return new Regex(correctedBR, "<br><b>Passwor(d|t):</b> <input").matches();
    }

    /**
     * Checks premiumonly status via current Browser-URL + HTML.
     *
     * @return isPremiumOnlyURL || isPremiumOnlyHTML
     */
    public boolean isPremiumOnly() {
        final boolean isPremiumonlyURL = isPremiumOnlyURL();
        final boolean isPremiumonlyHTML = isPremiumOnlyHTML();
        return isPremiumonlyURL || isPremiumonlyHTML;
    }

    /**
     * Checks premiumonly status via current Browser-URL.
     *
     * @return true: Link only downloadable for premium users (sometimes also for registered users). <br />
     *         false: Link is downloadable for all users.
     */
    public boolean isPremiumOnlyURL() {
        return br.getURL() != null && br.getURL().contains("/?op=login&redirect=");
    }

    /**
     * Checks premiumonly status via current Browser-HTML.
     *
     * @return true: Link only downloadable for premium users (sometimes also for registered users). <br />
     *         false: Link is downloadable for all users.
     */
    public boolean isPremiumOnlyHTML() {
        return new Regex(correctedBR, "( can download files up to |>\\s*Upgrade your account to download (?:larger|bigger) files|>\\s*The file you requested reached max downloads limit for Free Users|Please Buy Premium To download this file<|This file reached max downloads limit|>\\s*This file is available for Premium Users only|>\\s*Available Only for Premium Members|>File is available only for Premium users)").matches();
    }

    /**
     * @return true: Website is in maintenance mode - downloads are not possible but linkcheck may be possible. <br />
     *         false: Website is not in maintenance mode and should usually work fine.
     */
    public boolean isWebsiteUnderMaintenance() {
        return br.getHttpConnection().getResponseCode() == 500 || new Regex(correctedBR, "\">\\s*This server is in maintenance mode").matches();
    }

    public boolean isOffline(final DownloadLink link) {
        return isOffline(this.br, link);
    }

    /**
     * @return true: File is offline. <br />
     *         false: File should be online.
     */
    public boolean isOffline(final Browser br, final DownloadLink link) {
        return br.getHttpConnection().getResponseCode() == 404 || new Regex(correctedBR, "(No such file|>File Not Found<|>The file was removed by|Reason for deletion:\n|File Not Found|>The file expired)").matches();
    }

    /** Returns empty StringArray for filename, filesize, filehash, [more information in the future?] */
    protected String[] getFileInfoArray() {
        return new String[3];
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        final String[] fileInfo = getFileInfoArray();
        final String fallback_filename = this.getFallbackFilename(link);
        Browser altbr = null;
        fuid = null;
        correctDownloadLink(link);
        /* First, set fallback-filename */
        setWeakFilename(link);
        getPage(link.getPluginPatternMatcher());
        setFUID(link);
        if (isOffline(this.br, link)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        altbr = br.cloneBrowser();
        if (isWebsiteUnderMaintenance()) {
            /* In maintenance mode this sometimes is a way to find filenames! */
            if (this.supports_availablecheck_filename_abuse()) {
                fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
                if (!StringUtils.isEmpty(fileInfo[0])) {
                    link.setName(Encoding.htmlOnlyDecode(fileInfo[0]).trim());
                    return AvailableStatus.TRUE;
                }
            }
            return AvailableStatus.UNCHECKABLE;
        } else if (isPremiumOnlyURL()) {
            /*
             * Hosts whose urls are all premiumonly usually don't display any information about the URL at all - only maybe online/ofline.
             * There are 2 alternative ways to get this information anyways!
             */
            logger.info("PREMIUMONLY handling: Trying alternative linkcheck");
            if (this.supports_availablecheck_filename_abuse()) {
                fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
            }
            if (this.supports_availablecheck_alt()) {
                fileInfo[1] = getFilesizeViaAvailablecheckAlt(altbr, link);
            }
            if (!StringUtils.isEmpty(fileInfo[0]) || !StringUtils.isEmpty(fileInfo[1])) {
                /* We know the link must be online, lets set all information we got */
                link.setAvailable(true);
                if (!StringUtils.isEmpty(fileInfo[0])) {
                    link.setName(Encoding.htmlOnlyDecode(fileInfo[0].trim()));
                } else {
                    link.setName(fuid);
                }
                if (!StringUtils.isEmpty(fileInfo[1])) {
                    link.setDownloadSize(SizeFormatter.getSize(fileInfo[1]));
                }
                return AvailableStatus.TRUE;
            }
            logger.warning("Alternative linkcheck failed!");
            return AvailableStatus.UNCHECKABLE;
        }
        scanInfo(fileInfo);
        /**
         * TODO: Consider executing these advanced checks for linkcheck only - NOT if the user has just started downloads (--> Faster
         * downloadstart)
         */
        /* Filename abbreviated over x chars long --> Use getFnameViaAbuseLink as a workaround to find the full-length filename! */
        if (!StringUtils.isEmpty(fileInfo[0]) && fileInfo[0].trim().endsWith("&#133;") && this.supports_availablecheck_filename_abuse()) {
            logger.warning("filename length is larrrge");
            fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
        } else if (StringUtils.isEmpty(fileInfo[0]) && this.supports_availablecheck_filename_abuse()) {
            /* We failed to find the filename via html --> Try getFnameViaAbuseLink */
            logger.info("Failed to find filename, trying getFnameViaAbuseLink");
            fileInfo[0] = this.getFnameViaAbuseLink(altbr, link, fileInfo[0]);
        }
        if (StringUtils.isEmpty(fileInfo[0]) && this.isImagehoster()) {
            /*
             * Imagehosts often do not show any filenames, at least not on the first page plus they often have their abuse-url disabled. Add
             * ".jpg" extension so that linkgrabber filtering is possible although we do not y<et have our final filename.
             */
            fileInfo[0] = fallback_filename;
        } else if (StringUtils.isEmpty(fileInfo[0]) && allow_fallback_filename_on_parser_failure()) {
            /* Set fallback-filename if needed and allowed. */
            fileInfo[0] = fallback_filename;
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            /* This should never happen as we set fallback-filename if we fail to find a 'good filename' inside html. */
            logger.warning("filename equals null, throwing \"plugin defect\"");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (!StringUtils.isEmpty(fileInfo[2])) {
            link.setMD5Hash(fileInfo[2].trim());
        }
        /*
         * Decode HtmlEntity encoding in filename if needed.
         */
        if (Encoding.isHtmlEntityCoded(fileInfo[0])) {
            fileInfo[0] = Encoding.htmlDecode(fileInfo[0]);
        }
        /* Remove some html tags - in most cases not necessary! */
        fileInfo[0] = fileInfo[0].replaceAll("(</b>|<b>|\\.html)", "").trim();
        if (this.isVideohoster_enforce_video_filename()) {
            /* For videohosts we often get ugly filenames such as 'some_videotitle.avi.mkv.mp4' --> Correct that! */
            fileInfo[0] = this.removeDoubleExtensions(fileInfo[0], "mp4");
        }
        /* Finally set the name but do not yet set the finalFilename! */
        link.setName(fileInfo[0]);
        if (StringUtils.isEmpty(fileInfo[1]) && this.supports_availablecheck_alt()) {
            /*
             * We failed to find Do alt availablecheck here but don't check availibility based on alt availablecheck html because we already
             * know that the file must be online!
             */
            logger.info("Failed to find filesize --> Trying getFilesizeViaAvailablecheckAlt");
            fileInfo[1] = getFilesizeViaAvailablecheckAlt(altbr, link);
        }
        if (!StringUtils.isEmpty(fileInfo[1])) {
            link.setDownloadSize(SizeFormatter.getSize(fileInfo[1]));
        } else if (this.isVideohoster_2() && supports_availablecheck_filesize_via_videohoster_2_directurl()) {
            /* Last chance to find filesize */
            requestFileInformation_Embed(br, link, null, true);
        }
        return AvailableStatus.TRUE;
    }

    /**
     * 2019-05-15: This can check availability via '/embed' URL. <br />
     * Only call this if isVideohoster_2 is set to true.
     */
    protected void requestFileInformation_Embed(final Browser br, final DownloadLink link, final Account account, final boolean findFilesize) throws Exception {
        if (br.getURL() != null && !br.getURL().contains("/embed")) {
            final String embed_access = getMainPage() + "/embed-" + fuid + ".html";
            getPage(br, embed_access);
        }
        /*
         * Important: Do NOT use 404 as offline-indicator here as the website-owner could have simply disabled embedding while it was
         * enabled before --> This would return 404 for all '/embed' URLs! Only rely on precise errormessages!
         */
        // if (br.getHttpConnection().getResponseCode() == 404) {
        // throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        // }
        if (br.toString().equalsIgnoreCase("File was deleted")) {
            /* Should be valid for all XFS hosts e.g. speedvideo.net, uqload.com */
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        // final String url_thumbnail = getVideoThumbnailURL(br.toString());
        if (findFilesize) {
            final String dllink = getDllink(link, account, br, br.toString());
            /* Get- and set filesize from directurl */
            checkDirectLinkAndSetFilesize(link, dllink, true);
        }
    }

    /**
     * Tries to find filename, filesize and md5hash inside html. On Override, make sure to first use your special RegExes e.g.
     * fileInfo[0]="bla", THEN, if needed, call super.scanInfo(fileInfo). <br />
     * fileInfo[0] = filename, fileInfo[1] = filesize, fileInfo[2] = md5hash (rarely used)
     */
    public String[] scanInfo(final String[] fileInfo) {
        /*
         * 2019-04-17: TODO: Improve sharebox RegExes as this may save us from having to use other time-comsuming fallbacks such as
         * getFilesizeViaAvailablecheckAlt or getFnameViaAbuseLink. E.g. new XFS3 has good information in their shareboxes, example-hoster:
         * brupload.net
         */
        final String sharebox0 = "copy\\(this\\);.+>(.+) - ([\\d\\.]+ (?:B|KB|MB|GB))</a></textarea>[\r\n\t ]+</div>";
        final String sharebox1 = "copy\\(this\\);.+\\](.+) - ([\\d\\.]+ (?:B|KB|MB|GB))\\[/URL\\]";
        /* 2019-05-08: Sharebox with filename & filesize (bytes), example: snowfiles.com */
        final String sharebox2 = "\\[URL=https?://(?:www\\.)?[^/\"]+/" + this.fuid + "\\]([^\"/]*?)\\s*?\\-\\s*?(\\d+)\\[/URL\\]";
        /* standard traits from base page */
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, "You have requested.*?https?://(?:www\\.)?[^/]+/" + fuid + "/(.*?)</font>").getMatch(0);
            if (StringUtils.isEmpty(fileInfo[0])) {
                fileInfo[0] = new Regex(correctedBR, "fname\"( type=\"hidden\")? value=\"(.*?)\"").getMatch(1);
                if (StringUtils.isEmpty(fileInfo[0])) {
                    fileInfo[0] = new Regex(correctedBR, "<h2>Download File(.*?)</h2>").getMatch(0);
                    /* traits from download1 page below */
                    if (StringUtils.isEmpty(fileInfo[0])) {
                        fileInfo[0] = new Regex(correctedBR, "Filename:? ?(<[^>]+> ?)+?([^<>\"']+)").getMatch(1);
                    }
                }
            }
        }
        /* Next - details from sharing box (new to old) */
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, sharebox2).getMatch(0);
            if (StringUtils.isEmpty(fileInfo[0])) {
                fileInfo[0] = new Regex(correctedBR, sharebox1).getMatch(0);
                if (StringUtils.isEmpty(fileInfo[0])) {
                    fileInfo[0] = new Regex(correctedBR, sharebox0).getMatch(0);
                }
                if (StringUtils.isEmpty(fileInfo[0])) {
                    /* Link of the box without filesize */
                    fileInfo[0] = new Regex(correctedBR, "onFocus=\"copy\\(this\\);\">https?://(?:www\\.)?[^/]+/" + fuid + "/([^<>\"]*?)</textarea").getMatch(0);
                }
            }
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            fileInfo[0] = new Regex(correctedBR, "class=\"dfilename\">([^<>\"]*?)<").getMatch(0);
        }
        if (StringUtils.isEmpty(fileInfo[0])) {
            /* 2017-04-11: Typically for XVideoSharing sites */
            fileInfo[0] = new Regex(correctedBR, Pattern.compile("<title>Watch ([^<>\"]+)</title>", Pattern.CASE_INSENSITIVE)).getMatch(0);
        }
        /*
         * 2019-05-16: Experimenting´to find 'safe' filesize traits which can always be checked, regardless of the
         * 'supports_availablecheck_filesize_html' setting:
         */
        if (StringUtils.isEmpty(fileInfo[1])) {
            fileInfo[1] = new Regex(correctedBR, sharebox2).getMatch(1);
        }
        if (this.supports_availablecheck_filesize_html() && StringUtils.isEmpty(fileInfo[1])) {
            /** TODO: Clean this up */
            /* Starting from here - more unsafe attempts */
            if (StringUtils.isEmpty(fileInfo[1])) {
                fileInfo[1] = new Regex(correctedBR, "\\(([0-9]+ bytes)\\)").getMatch(0);
                if (StringUtils.isEmpty(fileInfo[1])) {
                    fileInfo[1] = getHighestVideoQualityFilesize();
                }
                if (StringUtils.isEmpty(fileInfo[1])) {
                    fileInfo[1] = new Regex(correctedBR, "</font>[ ]+\\(([^<>\"'/]+)\\)(.*?)</font>").getMatch(0);
                }
            }
            /* Next - unsafe details from sharing box */
            if (StringUtils.isEmpty(fileInfo[1])) {
                fileInfo[1] = new Regex(correctedBR, sharebox0).getMatch(1);
                if (StringUtils.isEmpty(fileInfo[1])) {
                    fileInfo[1] = new Regex(correctedBR, sharebox1).getMatch(1);
                }
            }
            /* Generic failover */
            if (StringUtils.isEmpty(fileInfo[1])) {
                fileInfo[1] = new Regex(correctedBR, "(\\d+(?:\\.\\d+)?(?: |\\&nbsp;)?(KB|MB|GB))").getMatch(0);
            }
        }
        /* MD5 is only available in very very rare cases! */
        if (StringUtils.isEmpty(fileInfo[2])) {
            fileInfo[2] = new Regex(correctedBR, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
        }
        return fileInfo;
    }

    /**
     * Use this to Override 'checkLinks(final DownloadLink[])' in supported plugins. <br />
     * Similar to getFilesizeViaAvailablecheckAlt <br />
     * <b>Use this only if:</b> <br />
     * - You have verified that the filehost has a mass-linkchecker and it is working fine with this code. <br />
     * - The contentURLs contain a filename as a fallback e.g. https://host.tld/<fuid>/someFilename.png.html
     */
    public boolean massLinkchecker(final DownloadLink[] urls) {
        if (urls == null || urls.length == 0) {
            return false;
        }
        try {
            final Browser br = new Browser();
            this.prepBrowser(br, getMainPage());
            br.setCookiesExclusive(true);
            final StringBuilder sb = new StringBuilder();
            final ArrayList<DownloadLink> links = new ArrayList<DownloadLink>();
            int index = 0;
            /* First let's find out which linkchecker to use ... */
            final String checkTypeOld = "checkfiles";
            final String checkTypeNew = "check_files";
            /* TODO: Use new settings for storing info here */
            final String checkType_last_used_and_working = this.getPluginConfig().getStringProperty("ALT_AVAILABLECHECK_LAST_WORKING", null);
            final String[] checkTypes;
            if (this.prefer_availablecheck_filesize_alt_type_old()) {
                checkTypes = new String[] { checkTypeOld, checkTypeNew };
            } else if (checkType_last_used_and_working != null) {
                /* Try to re-use last working method */
                if (checkType_last_used_and_working.equals(checkTypeNew)) {
                    checkTypes = new String[] { checkTypeNew, checkTypeOld };
                } else {
                    checkTypes = new String[] { checkTypeOld, checkTypeNew };
                }
            } else {
                checkTypes = new String[] { checkTypeNew, checkTypeOld };
            }
            String checkType = checkTypes[0];
            getPage(br, "https://" + this.getHost() + "?op=" + checkTypes[0]);
            final Form checkForm = br.getFormByInputFieldKeyValue("op", checkTypes[0]);
            if (checkForm == null) {
                /* Failed? Well then it can only be the other checkType --> Set- and use that */
                checkType = checkTypes[1];
            }
            while (true) {
                links.clear();
                while (true) {
                    /* we test 50 links at once */
                    if (index == urls.length || links.size() > 50) {
                        break;
                    }
                    links.add(urls[index]);
                    index++;
                }
                sb.delete(0, sb.capacity());
                sb.append("op=" + checkType + "&process=Check+URLs&list=");
                for (final DownloadLink dl : links) {
                    sb.append(dl.getPluginPatternMatcher());
                    sb.append("%0A");
                }
                postPage(br, "https://" + this.getHost() + "/?op=" + checkType, sb.toString());
                for (final DownloadLink dl : links) {
                    final String fuid = this.getFUIDFromURL(dl);
                    if (br.containsHTML(fuid + "</td><td style=\"color:red;\">Not found\\!</td>")) {
                        dl.setAvailable(false);
                    } else {
                        final String size = br.getRegex(fuid + "</td>\\s*?<td style=\"color:green;\">Found</td>\\s*?<td>([^<>\"]*?)</td>").getMatch(0);
                        dl.setAvailable(true);
                        if (size != null) {
                            /*
                             * Filesize should definitly be given - but at this stage we are quite sure that the file is online so let's not
                             * throw a fatal error if the filesize cannot be found.
                             */
                            dl.setDownloadSize(SizeFormatter.getSize(size));
                        }
                    }
                    /*
                     * We cannot get 'good' filenames via this call so we have to rely on our fallback-filenames (fuid or filename inside
                     * URL)!
                     */
                    setWeakFilename(dl);
                }
                if (index == urls.length) {
                    break;
                }
            }
        } catch (final Exception e) {
            return false;
        }
        return true;
    }

    /**
     * Try to find filename via '/?op=report_file&id=<fuid>'. Only call this function if supports_availablecheck_filename_abuse() is
     * enabled!<br />
     * E.g. needed if officially only logged in users can see filename or filename is missing in html code for whatever reason.<br />
     * Often needed for <b><u>IMAGEHOSTER</u> ' s</b>.<br />
     * Important: Only call this if <b><u>SUPPORTS_AVAILABLECHECK_ABUSE</u></b> is <b>true</b> (meaning omly try this if website supports
     * it)!<br />
     *
     * @throws Exception
     */
    public String getFnameViaAbuseLink(final Browser br, final DownloadLink dl, final String fallbackFilename) throws Exception {
        getPage(br, getMainPage() + "/?op=report_file&id=" + fuid, false);
        if (br.containsHTML(">No such file<")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = regexFilenameAbuse(br);
        if (filename == null) {
            filename = fallbackFilename;
        }
        return filename;
    }

    /** Part of getFnameViaAbuseLink(). */
    public String regexFilenameAbuse(final Browser br) {
        return br.getRegex("<b>Filename\\s*:?\\s*</b></td><td>([^<>\"]*?)</td>").getMatch(0);
    }

    /**
     * Get filename via mass-linkchecker/alternative availablecheck.<br />
     * Often used as fallback if o.g. officially only logged-in users can see filesize or filesize is not given in html code for whatever
     * reason.<br />
     * Often needed for <b><u>IMAGEHOSTER</u> ' s</b>.<br />
     * Important: Only call this if <b><u>isSupports_availablecheck_alt</u></b> is <b>true</b> (meaning omly try this if website supports
     * it)!<br />
     */
    public String getFilesizeViaAvailablecheckAlt(final Browser br, final DownloadLink link) throws PluginException {
        String filesize = null;
        String altAvailablecheckUrl = "";
        if (br.getURL() == null) {
            /* Browser has not been used before --> Use absolute path */
            altAvailablecheckUrl = getMainPage();
        }
        final String checkTypeOld = "checkfiles";
        final String checkTypeNew = "check_files";
        /* TODO: Use new settings for storing info here */
        final String checkType_last_used_and_working = this.getPluginConfig().getStringProperty("ALT_AVAILABLECHECK_LAST_WORKING", null);
        final String[] checkTypes;
        if (this.prefer_availablecheck_filesize_alt_type_old()) {
            /* Prefer to try old version of alt-availablecheck first. */
            checkTypes = new String[] { checkTypeOld, checkTypeNew };
        } else if (checkType_last_used_and_working != null) {
            /* Try to re-use last working method */
            if (checkType_last_used_and_working.equals(checkTypeNew)) {
                checkTypes = new String[] { checkTypeNew, checkTypeOld };
            } else {
                checkTypes = new String[] { checkTypeOld, checkTypeNew };
            }
        } else {
            checkTypes = new String[] { checkTypeNew, checkTypeOld };
        }
        for (final String checkType : checkTypes) {
            final String checkURL = altAvailablecheckUrl + "/?op=" + checkType;
            try {
                if (this.supports_availablecheck_filesize_alt_fast()) {
                    /* Quick way - we do not access the page before and do not need to parse the Form. */
                    postPage(br, checkURL, String.format("op=%s&process=Check+URLs&list=%s", checkType, URLEncode.encodeURIComponent(link.getPluginPatternMatcher())));
                } else {
                    /* Try to get the Form IF NEEDED as it can contain tokens which are missing otherwise. */
                    getPage(br, altAvailablecheckUrl);
                    Form checkfiles_form = null;
                    for (final String checkTypeTmp : checkTypes) {
                        checkfiles_form = br.getFormByInputFieldKeyValue("op", checkTypeTmp);
                        if (checkfiles_form != null) {
                            break;
                        }
                    }
                    if (checkfiles_form == null) {
                        logger.info("AltAvailablecheck: Failed to find check_files Form via checkType: " + checkType);
                        continue;
                    }
                    checkfiles_form.put("list", Encoding.urlEncode(link.getPluginPatternMatcher()));
                    submitForm(br, checkfiles_form);
                }
                filesize = br.getRegex(this.fuid + "</td>\\s*?<td style=\"color:green;\">Found</td>\\s*?<td>([^<>\"]*?)</td>").getMatch(0);
            } catch (final Throwable e) {
            }
            if (filesize != null) {
                logger.info("AltAvailablecheck: Successfully found filesize via checkType: " + checkType);
                /* Store info about working check-type to prefer this in the next linkcheck --> Speeds up linkcheck */
                this.getPluginConfig().setProperty("ALT_AVAILABLECHECK_LAST_WORKING", checkType);
                break;
            } else {
                logger.info("AltAvailablecheck: Failed to find filesize via checkType: " + checkType);
                /* Offline check */
                if (br.containsHTML("(>" + Pattern.quote(link.getPluginPatternMatcher()) + "</td><td style=\"color:red;\">Not found\\!</td>|" + this.fuid + " not found\\!</font>)")) {
                    /* SUPPORTS_AVAILABLECHECK_ABUSE == false and-or could not find any filename. */
                    logger.info("AltAvailablecheck: URL seems to be offline");
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
            }
        }
        return filesize;
    }

    /**
     * Removes double extensions (of video hosts) to correct ugly filenames such as 'some_videoname.mkv.flv.mp4'.<br />
     *
     * @param filename
     *            input filename whose extensions will be replaced by parameter defaultExtension.
     * @param desiredExtension
     *            Extension which is supposed to replace the (multiple) wrong extension(s). <br />
     *            If defaultExtension is null,this function will only remove existing extensions.
     */
    public String removeDoubleExtensions(String filename, final String desiredExtension) {
        if (filename == null || desiredExtension == null) {
            return filename;
        }
        /* First let's remove all [XVideosharing] common video extensions */
        final VideoExtensions[] videoExtensions = VideoExtensions.values();
        boolean foundExt = true;
        while (foundExt) {
            foundExt = false;
            /* Chek for video extensions at the end of the filename and remove them */
            for (final VideoExtensions videoExt : videoExtensions) {
                final Pattern pattern = videoExt.getPattern();
                final String extStr = pattern.toString();
                final Pattern removePattern = Pattern.compile(".+(( |\\.)" + extStr + ")$", Pattern.CASE_INSENSITIVE);
                final String removeThis = new Regex(filename, removePattern).getMatch(0);
                if (removeThis != null) {
                    filename = filename.replace(removeThis, "");
                    foundExt = true;
                    break;
                }
            }
        }
        /* Add desired video extension if given. */
        if (desiredExtension != null && !filename.endsWith("." + desiredExtension)) {
            filename += "." + desiredExtension;
        }
        return filename;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        doFree(downloadLink, null);
    }

    /** Handles pre-download forms & captcha for free (anonymous) + FREE ACCOUNT modes. */
    public void doFree(final DownloadLink link, final Account account) throws Exception, PluginException {
        /* 1. Bring up saved final links */
        final String directlinkproperty = getDownloadModeDirectlinkProperty(account);
        String dllink = checkDirectLink(link, directlinkproperty);
        /* 2. Check for streaming/direct links on the first page */
        if (dllink == null) {
            checkErrors(link, account, false);
            dllink = getDllink(link, account);
        }
        /* 3. Do they provide audio hosting? EXTREMELY rare case! */
        if (dllink == null && link.getName().endsWith(".mp3") && this.isAudiohoster()) {
            try {
                logger.info("Trying to get link via mp3embed");
                final Browser brv = br.cloneBrowser();
                getPage(brv, "/mp3embed-" + fuid, false);
                dllink = brv.getRedirectLocation();
                if (dllink == null) {
                    dllink = brv.getRegex("flashvars=\"file=(https?://[^<>\"]*?\\.mp3)\"").getMatch(0);
                }
                if (dllink == null) {
                    logger.info("Failed to get link via mp3embed because: " + br.toString());
                } else {
                    logger.info("Successfully found link via mp3embed");
                }
            } catch (final Throwable e) {
                logger.info("Failed to get link via mp3embed");
            }
        }
        /* 4. Do they provide video hosting? */
        if (dllink == null && this.isVideohoster()) {
            /* Legacy - most XFS videohosts do not support this anymore! */
            try {
                logger.info("Trying to get link via vidembed");
                final Browser brv = br.cloneBrowser();
                getPage(brv, "/vidembed-" + fuid, false);
                dllink = brv.getRedirectLocation();
                if (dllink == null) {
                    logger.info("Failed to get link via vidembed because: " + br.toString());
                } else {
                    logger.info("Successfully found link via vidembed");
                }
            } catch (final Throwable e) {
                logger.info("Failed to get link via vidembed");
            }
        }
        /* 5. Do they provide video hosting #2? */
        if (dllink == null && this.isVideohoster_2()) {
            try {
                logger.info("Trying to get link via embed");
                requestFileInformation_Embed(br, link, null, false);
                dllink = getDllink(link, account);
                if (dllink == null) {
                    logger.info("FAILED to get link via embed");
                } else {
                    logger.info("Successfully found link via embed");
                }
            } catch (final Throwable e) {
                logger.info("Failed to get link via embed");
            }
            if (dllink == null) {
                /* If failed, go back to the beginning */
                getPage(link.getPluginPatternMatcher());
            }
        }
        /* 6. Do we have an imagehost? */
        if (dllink == null && this.isImagehoster()) {
            checkErrors(link, account, false);
            Form imghost_next_form = null;
            do {
                imghost_next_form = findImageForm(this.br);
                if (imghost_next_form != null) {
                    imghost_next_form.remove("method_premium");
                    /* end of backward compatibility */
                    submitForm(imghost_next_form);
                    checkErrors(link, account, false);
                    dllink = getDllink(link, account);
                    /* For imagehosts, filenames are often not given until we can actually see/download the image! */
                    final String image_filename = new Regex(correctedBR, "class=\"pic\" alt=\"([^<>\"]*?)\"").getMatch(0);
                    if (image_filename != null) {
                        link.setName(Encoding.htmlOnlyDecode(image_filename));
                    }
                }
            } while (imghost_next_form != null);
        }
        /* 7. Continue like normal */
        if (dllink == null) {
            /*
             * Check errors here because if we don't and a link is premiumonly, download1 Form will be present, plugin will send it and most
             * likely end up with error "Fatal countdown error (countdown skipped)"
             */
            checkErrors(link, account, false);
            final Form download1 = findFormDownload1();
            if (download1 != null) {
                /* end of backward compatibility */
                submitForm(download1);
                checkErrors(link, account, false);
                dllink = getDllink(link, account);
            }
        }
        if (dllink == null) {
            final String highestVideoQualityHTML = getHighestQualityHTML();
            if (highestVideoQualityHTML != null) {
                final Regex videoinfo = new Regex(highestVideoQualityHTML, "download_video\\(\\'([a-z0-9]+)\\',\\'([^<>\"\\']*?)\\',\\'([^<>\"\\']*?)\\'");
                // final String vid = videoinfo.getMatch(0);
                /* Usually this will be 'o' standing for "original quality" */
                final String q = videoinfo.getMatch(1);
                final String hash = videoinfo.getMatch(2);
                if (q == null || hash == null) {
                    handlePluginBroken(link, "video_highest_quality_download_failure", 3);
                }
                getPage("/dl?op=download_orig_pre&id=" + this.fuid + "&mode=" + q + "&hash=" + hash);
            }
        }
        if (dllink == null) {
            Form dlForm = findFormF1();
            if (dlForm == null) {
                /* Last chance - maybe our errorhandling kicks in here. */
                checkErrors(link, account, false);
                /* Okay we finally have no idea what happened ... */
                handlePluginBroken(link, "dlform_f1_null", 3);
            }
            /* Define how many forms deep do we want to try? */
            int repeat = 2;
            for (int i = 0; i <= repeat; i++) {
                dlForm.remove(null);
                final long timeBefore = System.currentTimeMillis();
                if (isPasswordProtected()) {
                    logger.info("The downloadlink seems to be password protected.");
                    handlePassword(dlForm, link);
                }
                handleCaptcha(link, dlForm);
                /* 2019-02-08: MD5 can be on the subsequent pages - it is to be found very rare in current XFS versions */
                if (link.getMD5Hash() == null) {
                    final String md5hash = new Regex(correctedBR, "<b>MD5.*?</b>.*?nowrap>(.*?)<").getMatch(0);
                    if (md5hash != null) {
                        link.setMD5Hash(md5hash.trim());
                    }
                }
                waitTime(link, timeBefore);
                submitForm(dlForm);
                logger.info("Submitted DLForm");
                checkErrors(link, account, true);
                dllink = getDllink(link, account);
                if (dllink == null && (!br.containsHTML("<Form name=\"F1\" method=\"POST\" action=\"\"") || i == repeat)) {
                    logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } else if (dllink == null && br.containsHTML("<Form name=\"F1\" method=\"POST\" action=\"\"")) {
                    dlForm = findFormF1();
                    invalidateLastChallengeResponse();
                    continue;
                } else {
                    validateLastChallengeResponse();
                    break;
                }
            }
        }
        logger.info("Final downloadlink = " + dllink + " starting the download...");
        handleDownload(link, account, dllink);
    }

    /** Handles all kinds of captchas, also login-captcha - fills the given captchaForm. */
    public void handleCaptcha(final DownloadLink link, final Form captchaForm) throws Exception {
        /* Captcha START */
        if (correctedBR.contains("class=\"g-recaptcha\"")) {
            /*
             * 2017-12-07: New - solve- and check reCaptchaV2 here via ajax call, then wait- and submit the main downloadform. This might as
             * well be a workaround by the XFS developers to avoid expiring reCaptchaV2 challenges.
             */
            logger.info("Detected captcha method \"RecaptchaV2\" for this host");
            final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
            if (new Regex(correctedBR, Pattern.compile("\\$\\.post\\(\\s*?\"/ddl\"", Pattern.CASE_INSENSITIVE)).matches()) {
                /**
                 * TODO: Maybe add a check here to ensure that this part does not get accessed during login process as that would be a fatal
                 * failure!
                 */
                /* 2017-12-07: New - this case can only happen during download and cannot be part of the login process! */
                /* Do not put the result in this Form as the check is handled below already */
                captchaForm.put("g-recaptcha-response", "");
                final Form ajaxCaptchaForm = new Form();
                ajaxCaptchaForm.setMethod(MethodType.POST);
                ajaxCaptchaForm.setAction("/ddl");
                final InputField if_Rand = captchaForm.getInputFieldByName("rand");
                final String file_id = PluginJSonUtils.getJson(br, "file_id");
                if (if_Rand != null) {
                    /* This is usually given */
                    ajaxCaptchaForm.put("rand", if_Rand.getValue());
                }
                if (!StringUtils.isEmpty(file_id)) {
                    /* This is usually given */
                    ajaxCaptchaForm.put("file_id", file_id);
                }
                ajaxCaptchaForm.put("op", "captcha1");
                ajaxCaptchaForm.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                /* User existing Browser object as we get a cookie which is required later. */
                br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                this.submitForm(br, ajaxCaptchaForm);
                if (!br.toString().equalsIgnoreCase("OK")) {
                    logger.warning("Fatal reCaptchaV2 ajax handling failure");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.getHeaders().remove("X-Requested-With");
            } else {
                /* 2019-02-08: 'Old' but still the most used case */
                captchaForm.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
            }
        } else {
            if (correctedBR.contains(";background:#ccc;text-align")) {
                logger.info("Detected captcha method \"plaintext captchas\" for this host");
                /* Captcha method by ManiacMansion */
                final String[][] letters = new Regex(br, "<span style='position:absolute;padding\\-left:(\\d+)px;padding\\-top:\\d+px;'>(&#\\d+;)</span>").getMatches();
                if (letters == null || letters.length == 0) {
                    logger.warning("plaintext captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                final SortedMap<Integer, String> capMap = new TreeMap<Integer, String>();
                for (String[] letter : letters) {
                    capMap.put(Integer.parseInt(letter[0]), Encoding.htmlDecode(letter[1]));
                }
                final StringBuilder code = new StringBuilder();
                for (String value : capMap.values()) {
                    code.append(value);
                }
                captchaForm.put("code", code.toString());
                logger.info("Put captchacode " + code.toString() + " obtained by captcha metod \"plaintext captchas\" in the form.");
            } else if (correctedBR.contains("/captchas/")) {
                logger.info("Detected captcha method \"Standard captcha\" for this host");
                final String[] sitelinks = HTMLParser.getHttpLinks(br.toString(), null);
                String captchaurl = null;
                if (sitelinks == null || sitelinks.length == 0) {
                    logger.warning("Standard captcha captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                for (final String linkTmp : sitelinks) {
                    if (linkTmp.contains("/captchas/")) {
                        captchaurl = linkTmp;
                        break;
                    }
                }
                if (captchaurl == null) {
                    logger.warning("Standard captcha captchahandling broken!");
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                String code = getCaptchaCode("xfilesharingprobasic", captchaurl, link);
                captchaForm.put("code", code);
                logger.info("Put captchacode " + code + " obtained by captcha metod \"Standard captcha\" in the form.");
            } else if (new Regex(correctedBR, "(api\\.recaptcha\\.net|google\\.com/recaptcha/api/)").matches()) {
                logger.info("Detected captcha method \"reCaptchaV1\" for this host");
                throw new PluginException(LinkStatus.ERROR_FATAL, "Website uses reCaptchaV1 which has been shut down by Google. Contact website owner!");
            } else if (new Regex(correctedBR, "solvemedia\\.com/papi/").matches()) {
                logger.info("Detected captcha method \"solvemedia\" for this host");
                final org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia sm = new org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia(br);
                File cf = null;
                try {
                    cf = sm.downloadCaptcha(getLocalCaptchaFile());
                } catch (final Exception e) {
                    if (org.jdownloader.captcha.v2.challenge.solvemedia.SolveMedia.FAIL_CAUSE_CKEY_MISSING.equals(e.getMessage())) {
                        throw new PluginException(LinkStatus.ERROR_FATAL, "Host side solvemedia.com captcha error - please contact the " + this.getHost() + " support");
                    }
                    throw e;
                }
                final String code = getCaptchaCode("solvemedia", cf, link);
                final String chid = sm.getChallenge(code);
                captchaForm.put("adcopy_challenge", chid);
                captchaForm.put("adcopy_response", "manual_challenge");
            } else if (br.containsHTML("id=\"capcode\" name= \"capcode\"")) {
                logger.info("Detected captcha method \"keycaptca\"");
                String result = handleCaptchaChallenge(getDownloadLink(), new KeyCaptcha(this, br, getDownloadLink()).createChallenge(this));
                if (result == null) {
                    throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                }
                if ("CANCEL".equals(result)) {
                    throw new PluginException(LinkStatus.ERROR_FATAL);
                }
                captchaForm.put("capcode", result);
            }
            /* Captcha END */
        }
    }

    /** Tries to find 1st download Form for free download. */
    public Form findFormDownload1() throws Exception {
        final Form download1 = br.getFormByInputFieldKeyValue("op", "download1");
        if (download1 != null) {
            download1.remove("method_premium");
            /* Fix/Add "method_free" value if necessary. */
            if (!download1.hasInputFieldByName("method_free") || download1.getInputFieldByName("method_free").getValue() == null) {
                String method_free_value = download1.getRegex("\"method_free\" value=\"([^<>\"]+)\"").getMatch(0);
                if (method_free_value == null || method_free_value.equals("")) {
                    method_free_value = "Free Download";
                }
                download1.put("method_free", Encoding.urlEncode(method_free_value));
            }
        }
        return download1;
    }

    /** Tries to find 2nd download Form for free download. */
    public Form findFormF1() {
        Form dlForm = null;
        /* First try to find Form for video hosts with multiple qualities. */
        final Form[] forms = br.getForms();
        for (final Form aForm : forms) {
            final InputField op_field = aForm.getInputFieldByName("op");
            /* E.g. name="op" value="download_orig" */
            if (aForm.containsHTML("btn_download") && op_field != null && op_field.getValue().contains("download_")) {
                dlForm = aForm;
                break;
            }
        }
        /* Nothing found? Fallback to standard download handling! */
        if (dlForm == null) {
            dlForm = br.getFormbyProperty("name", "F1");
        }
        return dlForm;
    }

    /**
     * Tries to find download Form for premium download.
     *
     * @throws Exception
     */
    public Form findFormF1Premium() throws Exception {
        return br.getFormbyProperty("name", "F1");
    }

    /**
     * Checks if there are multiple video qualities available, finds html containing information of the highest video quality and returns
     * corresponding filesize if given.
     */
    public String getHighestQualityHTML() {
        final String[] videoQualities = new Regex(correctedBR, "<tr>\\s*?<td>\\s*?<input[^>]*?onclick=\"download_video.*?</tr>").getColumn(-1);
        long widthMax = 0;
        long widthTmp = 0;
        String targetHTML = null;
        for (final String videoQualityHTML : videoQualities) {
            final String filesizeTmpStr = new Regex(videoQualityHTML, "<td>(\\d+)x\\d+, \\d+[^<>\"]+</td>").getMatch(0);
            if (filesizeTmpStr != null) {
                widthTmp = SizeFormatter.getSize(filesizeTmpStr);
                if (widthTmp > widthMax) {
                    widthMax = widthTmp;
                    targetHTML = videoQualityHTML;
                }
            } else {
                /* This should not happen */
                break;
            }
        }
        return targetHTML;
    }

    /**
     * Returns filesize for highest video quality found via getHighestQualityHTML. <br />
     * This function is rarely used!
     */
    public String getHighestVideoQualityFilesize() {
        final String highestVideoQualityHTML = getHighestQualityHTML();
        return new Regex(highestVideoQualityHTML, "<td>\\d+x\\d+, (\\d+[^<>\"]+)</td>").getMatch(0);
    }

    /**
     * Check if a stored directlink exists under property 'property' and if so, check if it is still valid (leads to a downloadable content
     * [NOT html]).
     */
    public String checkDirectLink(final DownloadLink downloadLink, final String property) {
        final String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            final String ret = checkDirectLinkAndSetFilesize(downloadLink, dllink, false);
            if (ret != null) {
                return ret;
            } else {
                downloadLink.removeProperty(property);
                return null;
            }
        }
        return null;
    }

    /**
     * Checks if a directurl leads to downloadable content and if so, returns true. <br />
     * This will also return true if the serverside connection limit has been reached. <br />
     *
     * @param link
     *            : The DownloadLink
     * @param directurl
     *            : Directurl which should lead to downloadable content
     * @param setFilesize
     *            : true = setVerifiedFileSize filesize if directurl is really downloadable
     */
    public String checkDirectLinkAndSetFilesize(final DownloadLink link, final String directurl, final boolean setFilesize) {
        if (StringUtils.isEmpty(directurl) || !directurl.startsWith("http")) {
            return null;
        } else {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = openAntiDDoSRequestConnection(br2, br2.createHeadRequest(directurl));
                /* For video streams we often don't get a Content-Disposition header. */
                final boolean isFile = con.isContentDisposition() || StringUtils.containsIgnoreCase(con.getContentType(), "video") || StringUtils.containsIgnoreCase(con.getContentType(), "audio") || StringUtils.containsIgnoreCase(con.getContentType(), "application");
                if (con.getResponseCode() == 503) {
                    /* Ok */
                    /*
                     * Too many connections but that does not mean that our directlink is invalid. Accept it and if it still returns 503 on
                     * download-attempt this error will get displayed to the user - such directlinks should work again once there are less
                     * active connections to the host!
                     */
                    logger.info("directurl lead to 503 | too many connections");
                    return directurl;
                } else if (!con.getContentType().contains("html") && con.getLongContentLength() > -1 && con.isOK() && isFile) {
                    if (setFilesize) {
                        link.setVerifiedFileSize(con.getLongContentLength());
                    }
                    return directurl;
                } else {
                    /* Failure */
                }
            } catch (final Exception e) {
                /* Failure */
                logger.log(e);
            } finally {
                if (con != null) {
                    try {
                        con.disconnect();
                    } catch (final Throwable e) {
                    }
                }
            }
            return null;
        }
    }

    @Override
    public boolean hasAutoCaptcha() {
        return false;
    }

    @Override
    public boolean hasCaptcha(DownloadLink link, jd.plugins.Account acc) {
        if (acc == null || acc.getType() == AccountType.FREE) {
            /* Free accounts can have captchas */
            return true;
        } else {
            /* Premium accounts do not have captchas */
            return false;
        }
    }

    public ArrayList<String> getCleanupHTMLRegexes() {
        final ArrayList<String> regexStuff = new ArrayList<String>();
        // remove custom rules first!!! As html can change because of generic cleanup rules.
        /* generic cleanup */
        regexStuff.add("<\\!(\\-\\-.*?\\-\\-)>");
        regexStuff.add("(display: ?none;\">.*?</div>)");
        regexStuff.add("(visibility:hidden>.*?<)");
        return regexStuff;
    }

    /* Removes HTML code which could break the plugin */
    protected void correctBR() throws NumberFormatException, PluginException {
        correctedBR = br.toString();
        final ArrayList<String> regexStuff = getCleanupHTMLRegexes();
        // remove custom rules first!!! As html can change because of generic cleanup rules.
        /* generic cleanup */
        for (String aRegex : regexStuff) {
            String results[] = new Regex(correctedBR, aRegex).getColumn(0);
            if (results != null) {
                for (String result : results) {
                    correctedBR = correctedBR.replace(result, "");
                }
            }
        }
    }

    protected String getDllink(DownloadLink downloadLink, Account account) {
        return getDllink(downloadLink, account, this.br, correctedBR);
    }

    /** Function to find the final downloadlink. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected String getDllink(final DownloadLink downloadLink, final Account account, final Browser br, String src) {
        String dllink = br.getRedirectLocation();
        if (dllink == null || new Regex(dllink, this.getSupportedLinks()).matches()) {
            // dllink = new Regex(src, "(\"|')(" + String.format(dllinkRegexFile, getHostsPatternPart()) + ")\\1").getMatch(1);
            // /* Use wider and wider RegEx */
            // if (dllink == null) {
            // dllink = new Regex(src, "(" + String.format(dllinkRegexFile, getHostsPatternPart()) + ")(\"|')").getMatch(0);
            // }
            if (dllink == null) {
                /* Finally try without hardcoded domains */
                dllink = new Regex(src, "(" + String.format(getGenericDownloadlinkRegExFile(), "[A-Za-z0-9\\-\\.]+") + ")(\"|')").getMatch(0);
            }
            // if (dllink == null) {
            // /* Try short version */
            // dllink = new Regex(src, "(\"|')(" + String.format(dllinkRegexFile_2, getHostsPatternPart()) + ")\\1").getMatch(1);
            // }
            // if (dllink == null) {
            // /* Try short version without hardcoded domains and wide */
            // dllink = new Regex(src, "(" + String.format(dllinkRegexFile_2, getHostsPatternPart()) + ")").getMatch(0);
            // }
            /* 2019-02-02: TODO: Add attempt to find downloadlink by the first url which ends with the filename */
            if (dllink == null) {
                final String cryptedScripts[] = new Regex(src, "p\\}\\((.*?)\\.split\\('\\|'\\)").getColumn(0);
                if (cryptedScripts != null && cryptedScripts.length != 0) {
                    for (String crypted : cryptedScripts) {
                        dllink = decodeDownloadLink(crypted);
                        if (dllink != null) {
                            break;
                        }
                    }
                }
            }
        }
        if (dllink == null) {
            /* RegExes sometimes used for streaming */
            final String jssource = new Regex(src, "sources\\s*?:\\s*?(\\[.*?\\])").getMatch(0);
            if (StringUtils.isEmpty(dllink) && jssource != null) {
                try {
                    HashMap<String, Object> entries = null;
                    Object quality_temp_o = null;
                    long quality_temp = 0;
                    long quality_best = 0;
                    String dllink_temp = null;
                    final ArrayList<Object> ressourcelist = (ArrayList) JavaScriptEngineFactory.jsonToJavaObject(jssource);
                    for (final Object videoo : ressourcelist) {
                        if (videoo instanceof String && ressourcelist.size() == 1) {
                            /* Maybe single URL without any quality information e.g. uqload.com */
                            dllink_temp = (String) videoo;
                            if (dllink_temp.startsWith("http")) {
                                dllink = dllink_temp;
                                break;
                            }
                        }
                        entries = (HashMap<String, Object>) videoo;
                        dllink_temp = (String) entries.get("file");
                        quality_temp_o = entries.get("label");
                        if (quality_temp_o != null && quality_temp_o instanceof Long) {
                            quality_temp = JavaScriptEngineFactory.toLong(quality_temp_o, 0);
                        } else if (quality_temp_o != null && quality_temp_o instanceof String) {
                            /* E.g. '360p' */
                            quality_temp = Long.parseLong(new Regex((String) quality_temp_o, "(\\d+)p").getMatch(0));
                        }
                        if (StringUtils.isEmpty(dllink_temp) || quality_temp == 0) {
                            continue;
                        } else if (dllink_temp.contains(".m3u8")) {
                            /* Skip hls */
                            continue;
                        }
                        if (quality_temp > quality_best) {
                            quality_best = quality_temp;
                            dllink = dllink_temp;
                        }
                    }
                    if (!StringUtils.isEmpty(dllink)) {
                        logger.info("BEST handling for multiple video source succeeded");
                    }
                } catch (final Throwable e) {
                    logger.info("BEST handling for multiple video source failed");
                }
            }
            if (StringUtils.isEmpty(dllink)) {
                final String check = new Regex(src, "file\\s*?:\\s*?\"(https?[^<>\"]*?\\.(?:mp4|flv))\"").getMatch(0);
                if (StringUtils.isNotEmpty(check) && !StringUtils.containsIgnoreCase(check, "/images/")) {
                    // jwplayer("flvplayer").onError(function()...
                    dllink = check;
                }
            }
        }
        if (dllink == null && this.isImagehoster()) {
            /* Used for image-hosts */
            String[] possibleDllinks = null;
            // possibleDllinks = new Regex(this.src, String.format(dllinkRegexImage, getHostsPatternPart())).getColumn(0);
            if (possibleDllinks == null || possibleDllinks.length == 0) {
                /* Try without predefined domains */
                possibleDllinks = new Regex(src, String.format(getGenericDownloadlinkRegExImage(), "[A-Za-z0-9\\-\\.]+")).getColumn(-1);
            }
            for (final String possibleDllink : possibleDllinks) {
                /* Do NOT download thumbnails! */
                if (possibleDllink != null && !possibleDllink.matches(".+_t\\.[A-Za-z]{3,4}$")) {
                    dllink = possibleDllink;
                    break;
                }
            }
        }
        return dllink;
    }

    /**
     * Returns URL to the video thumbnail. <br />
     * This might sometimes be useful when VIDEOHOSTER or VIDEOHOSTER_2 handling is used.
     */
    public String getVideoThumbnailURL(final String src) {
        String url_thumbnail = new Regex(src, "image\\s*?:\\s*?\"(https?://[^<>\"]+)\"").getMatch(0);
        if (StringUtils.isEmpty(url_thumbnail)) {
            /* 2019-05-16: e.g. uqload.com */
            url_thumbnail = new Regex(src, "poster\\s*?:\\s*?\"(https?://[^<>\"]+)\"").getMatch(0);
        }
        return url_thumbnail;
    }

    public String decodeDownloadLink(final String s) {
        String decoded = null;
        try {
            Regex params = new Regex(s, "'(.*?[^\\\\])',(\\d+),(\\d+),'(.*?)'");
            String p = params.getMatch(0).replaceAll("\\\\", "");
            int a = Integer.parseInt(params.getMatch(1));
            int c = Integer.parseInt(params.getMatch(2));
            String[] k = params.getMatch(3).split("\\|");
            while (c != 0) {
                c--;
                if (k[c].length() != 0) {
                    p = p.replaceAll("\\b" + Integer.toString(c, a) + "\\b", k[c]);
                }
            }
            decoded = p;
        } catch (Exception e) {
        }
        String finallink = null;
        if (decoded != null) {
            /* Open regex is possible because in the unpacked JS there are usually only 1-2 URLs. */
            finallink = new Regex(decoded, "(?:\"|')(https?://[^<>\"']*?\\.(avi|flv|mkv|mp4))(?:\"|')").getMatch(0);
            if (finallink == null) {
                /* Maybe rtmp */
                finallink = new Regex(decoded, "(?:\"|')(rtmp://[^<>\"']*?mp4:[^<>\"']+)(?:\"|')").getMatch(0);
            }
        }
        return finallink;
    }

    public boolean isDllinkFile(final String url) {
        if (url == null) {
            return false;
        }
        return new Regex(url, Pattern.compile(String.format(getGenericDownloadlinkRegExFile(), "[A-Za-z0-9\\-\\.]+"), Pattern.CASE_INSENSITIVE)).matches();
    }

    public boolean isDllinkImage(final String url) {
        if (url == null) {
            return false;
        }
        return new Regex(url, Pattern.compile(String.format(getGenericDownloadlinkRegExFile(), "[A-Za-z0-9\\-\\.]+"), Pattern.CASE_INSENSITIVE)).matches();
    }

    /** Returns pre-download-waittime (seconds) from inside HTML. */
    public String regexWaittime() {
        /**
         * TODO: 2019-05-15: Try to grab the whole line which contains "id":"countdown" and then grab the waittime from inside that as it
         * would probably make this more reliable.
         */
        /* Ticket Time */
        String ttt = new Regex(correctedBR, "id=\"countdown_str\"[^>]*?>[^<>\"]*?<span id=\"[^<>\"]+\"(?:[^<>]+)?>(?:\\s+)?(\\d+)(?:\\s+)?</span>").getMatch(0);
        if (ttt == null) {
            ttt = new Regex(correctedBR, "id=\"countdown_str\" style=\"[^<>\"]+\">Wait <span id=\"[A-Za-z0-9]+\">(\\d+)</span>").getMatch(0);
        }
        if (ttt == null) {
            ttt = new Regex(correctedBR, "id=\"countdown_str\">Wait <span id=\"[A-Za-z0-9]+\">(\\d+)</span>").getMatch(0);
        }
        if (ttt == null) {
            ttt = new Regex(correctedBR, "class=\"seconds\"[^>]*?>\\s*?(\\d+)\\s*?</span>").getMatch(0);
        }
        if (ttt == null) {
            /* More open RegEx */
            ttt = new Regex(correctedBR, "class=\"seconds\">\\s*?(\\d+)\\s*?<").getMatch(0);
        }
        return ttt;
    }

    public String getGenericDownloadlinkRegExFile() {
        return "https?://(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|(?:[\\w\\-\\.]+\\.)?%s)(?::\\d{1,4})?/(?:files|d|cgi\\-bin/dl\\.cgi)/(?:\\d+/)?[a-z0-9]+/[^<>\"/]*?";
    }

    /*
     * Alternative / old:
     * https?://(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|(?:[\\w\\-\\.]+\\.)?%s)(?::\\d{1,4})?/[a-z0-9]{50,}/[^<>\"/]*?
     */
    public String getGenericDownloadlinkRegExImage() {
        return "https?://(?:\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|(?:[\\w\\-\\.]+\\.)?%s)(?:/img/\\d+/[^<>\"'\\[\\]]+|/img/[a-z0-9]+/[^<>\"'\\[\\]]+|/img/[^<>\"'\\[\\]]+|/i/\\d+/[^<>\"'\\[\\]]+|/i/\\d+/[^<>\"'\\[\\]]+(?!_t\\.[A-Za-z]{3,4}))";
    }

    /**
     * Prevents more than one free download from starting at a given time. One step prior to dl.startDownload(), it adds a slot to maxFree
     * which allows the next singleton download to start, or at least try.
     *
     * This is needed because xfileshare(website) only throws errors after a final dllink starts transferring or at a given step within pre
     * download sequence. But this template(XfileSharingProBasic) allows multiple slots(when available) to commence the download sequence,
     * this.setstartintival does not resolve this issue. Which results in x(20) captcha events all at once and only allows one download to
     * start. This prevents wasting peoples time and effort on captcha solving and|or wasting captcha trading credits. Users will experience
     * minimal harm to downloading as slots are freed up soon as current download begins.
     *
     * @param num
     *            : (+1|-1)
     */
    protected synchronized void controlFree(final int num) {
        logger.info("maxFree was = " + maxFree.get());
        maxFree.set(Math.min(Math.max(1, maxFree.addAndGet(num)), totalMaxSimultanFreeDownload.get()));
        logger.info("maxFree now = " + maxFree.get());
    }

    @Override
    protected void getPage(String page) throws Exception {
        getPage(br, page, true);
    }

    protected void getPage(final Browser br, String page, final boolean correctBr) throws Exception {
        getPage(br, page);
        if (correctBr) {
            correctBR();
        }
    }

    @Override
    protected void postPage(String page, final String postdata) throws Exception {
        postPage(br, page, postdata, true);
    }

    protected void postPage(final Browser br, String page, final String postdata, final boolean correctBr) throws Exception {
        postPage(br, page, postdata);
        if (correctBr) {
            correctBR();
        }
    }

    @Override
    protected void submitForm(final Form form) throws Exception {
        submitForm(br, form, true);
    }

    protected void submitForm(final Browser br, final Form form, final boolean correctBr) throws Exception {
        submitForm(br, form);
        if (correctBr) {
            correctBR();
        }
    }

    /**
     * Handles pre download (pre-captcha) waittime. If WAITFORCED it ensures to always wait long enough even if the waittime RegEx fails.
     */
    protected void waitTime(final DownloadLink downloadLink, final long timeBefore) throws PluginException {
        /* Ticket Time */
        final String waitStr = regexWaittime();
        if (this.preDownloadWaittimeSkippable()) {
            logger.info("Skipping pre-download waittime: " + waitStr);
        } else {
            final int extraWaitSeconds = 1;
            int wait = 0;
            int passedTime = (int) ((System.currentTimeMillis() - timeBefore) / 1000) - extraWaitSeconds;
            if (waitStr != null && waitStr.matches("\\d+")) {
                logger.info("Found waittime, parsing waittime: " + waitStr);
                wait = Integer.parseInt(waitStr);
                /*
                 * Waittime found in html but plugin developer set min- and max times? Check and fallback to getWaitsecondsforced if needed.
                 */
                if (this.forcePreDownloadWaittime() && (wait > this.getWaitsecondsmax() || wait < this.getWaitsecondsmin())) {
                    logger.warning("Wait exceeds max/min, using forced wait!");
                    wait = this.getWaitsecondsforced();
                }
            } else if (this.forcePreDownloadWaittime()) {
                logger.info("Failed to find waittime - using forced pre-download waittime");
                /* Get random waittime > Waitsecondsmin */
                int i = 0;
                while (i < this.getWaitsecondsmin()) {
                    i += new Random().nextInt(this.getWaitsecondsmin());
                }
                wait = i;
            }
            /*
             * Check how much time has passed during eventual captcha event before this function has been called and see how much time is
             * left to wait.
             */
            wait -= passedTime;
            if (passedTime > 0) {
                /* This usually means that the user had to solve a captcha which cuts down the remaining time we have to wait. */
                logger.info("Total passed time during captcha: " + passedTime);
            }
            if (wait > 0) {
                logger.info("Waiting waittime: " + wait);
                sleep(wait * 1000l, downloadLink);
            } else if (wait < -extraWaitSeconds) {
                /* User needed more time to solve the captcha so there is no waittime left :) */
                logger.info("Congratulations: Time to solve captcha was higher than waittime");
            } else {
                /* No waittime at all */
                logger.info("Found no waittime");
            }
        }
    }

    /**
     * This fixes filenames from all xfs modules: file hoster, audio/video streaming (including transcoded video), or blocked link checking
     * which is based on fuid.
     *
     * @version 0.4
     * @author raztoki
     */
    protected void fixFilename(final DownloadLink downloadLink) {
        String orgName = null;
        String orgExt = null;
        String servName = null;
        String servExt = null;
        String orgNameExt = downloadLink.getFinalFileName();
        if (orgNameExt == null) {
            orgNameExt = downloadLink.getName();
        }
        if (!StringUtils.isEmpty(orgNameExt) && orgNameExt.contains(".")) {
            orgExt = orgNameExt.substring(orgNameExt.lastIndexOf("."));
        }
        if (!StringUtils.isEmpty(orgExt)) {
            orgName = new Regex(orgNameExt, "(.+)" + Pattern.quote(orgExt)).getMatch(0);
        } else {
            orgName = orgNameExt;
        }
        // if (orgName.endsWith("...")) orgName = orgName.replaceFirst("\\.\\.\\.$", "");
        String servNameExt = dl.getConnection() != null && getFileNameFromHeader(dl.getConnection()) != null ? Encoding.htmlDecode(getFileNameFromHeader(dl.getConnection())) : null;
        if (!StringUtils.isEmpty(servNameExt) && servNameExt.contains(".")) {
            servExt = servNameExt.substring(servNameExt.lastIndexOf("."));
            servName = new Regex(servNameExt, "(.+)" + Pattern.quote(servExt)).getMatch(0);
        } else {
            servName = servNameExt;
        }
        String FFN = null;
        if (orgName.equalsIgnoreCase(fuid.toLowerCase())) {
            FFN = servNameExt;
        } else if (StringUtils.isEmpty(orgExt) && !StringUtils.isEmpty(servExt) && (servName.toLowerCase().contains(orgName.toLowerCase()) && !servName.equalsIgnoreCase(orgName))) {
            /*
             * when partial match of filename exists. eg cut off by quotation mark miss match, or orgNameExt has been abbreviated by hoster
             */
            FFN = servNameExt;
        } else if (!StringUtils.isEmpty(orgExt) && !StringUtils.isEmpty(servExt) && !orgExt.equalsIgnoreCase(servExt)) {
            FFN = orgName + servExt;
        } else {
            FFN = orgNameExt;
        }
        downloadLink.setFinalFileName(FFN);
    }

    /**
     * Sets XFS file-ID which is usually present inside the downloadurl added by the user. Usually it is [a-z0-9]{12}. <br />
     * Best to execute AFTER having accessed the downloadurl!
     */
    protected void setFUID(final DownloadLink dl) throws PluginException {
        fuid = getFUIDFromURL(dl);
        /*
         * Rare case: Hoster has exotic URLs (e.g. migrated from other script e.g. YetiShare to XFS) --> Correct (internal) fuid is only
         * available via html
         */
        if (fuid == null) {
            /*
             * E.g. for hosts which migrate from other scripts such as YetiShare to XFS (example: hugesharing.net) and still have their old
             * URLs without XFS-fuid redirecting to the typical XFS URLs containing our fuid.
             */
            logger.info("fuid not given inside URL, trying to find it inside html");
            fuid = new Regex(correctedBR, "type=\"hidden\" name=\"id\" value=\"([a-z0-9]{12})\"").getMatch(0);
            if (fuid == null) {
                /* Last chance fallback */
                fuid = new Regex(br.getURL(), "https?://[^/]+/([a-z0-9]{12})").getMatch(0);
            }
            if (fuid == null) {
                /* fuid is crucial for us to have!! */
                logger.warning("Failed to find fuid inside html");
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            logger.info("Found fuid inside html: " + fuid);
            correctDownloadLink(dl);
        }
    }

    /** Returns unique id from inside URL - usually with this pattern: [a-z0-9]{12} */
    public String getFUIDFromURL(final DownloadLink dl) {
        try {
            final String result = new Regex(new URL(dl.getPluginPatternMatcher()).getPath(), "/(?:embed\\-)?([a-z0-9]{12})").getMatch(0);
            return result;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** In some cases, URL may contain filename which can be used as fallback e.g. 'https://host.tld/<fuid>/<filename>.html'. */
    public String getFilenameFromURL(final DownloadLink dl) {
        try {
            String result = null;
            if (dl.getContentUrl() != null) {
                result = new Regex(new URL(dl.getContentUrl()).getPath(), "[a-z0-9]{12}/(.+)\\.html$").getMatch(0);
            }
            if (result == null) {
                result = new Regex(new URL(dl.getPluginPatternMatcher()).getPath(), "[a-z0-9]{12}/(.+)\\.html$").getMatch(0);
            }
            return result;
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /** Tries to get filename from URL and if this fails, will return <fuid> filename. */
    public String getFallbackFilename(final DownloadLink dl) {
        String fallback_filename = this.getFilenameFromURL(dl);
        if (fallback_filename == null) {
            fallback_filename = this.getFUIDFromURL(dl);
        }
        return fallback_filename;
    }

    public void handlePassword(final Form pwform, final DownloadLink thelink) throws PluginException {
        String passCode = thelink.getDownloadPassword();
        if (passCode == null) {
            passCode = Plugin.getUserInput("Password?", thelink);
            if (StringUtils.isEmpty(passCode)) {
                logger.info("User has entered blank password, exiting handlePassword");
                thelink.setDownloadPassword(null);
                throw new PluginException(LinkStatus.ERROR_FATAL, "Pre-Download Password not provided");
            }
        }
        logger.info("Put password \"" + passCode + "\" entered by user in the DLForm.");
        pwform.put("password", Encoding.urlEncode(passCode));
        thelink.setDownloadPassword(passCode);
        return;
    }

    /**
     * Checks for (-& handles) all kinds of errors e.g. wrong captcha, wrong downloadpassword, waittimes and server error-responsecodes such
     * as 403, 404 and 503. <br />
     * checkAll: If enabled, ,this will also check for wrong password, wrong captcha and 'Skipped countdown' errors.
     */
    public void checkErrors(final DownloadLink link, final Account account, final boolean checkAll) throws NumberFormatException, PluginException {
        if (checkAll) {
            if (isPasswordProtected() && correctedBR.contains("Wrong password")) {
                final String userEnteredPassword = link.getDownloadPassword();
                /* handle password has failed in the past, additional try catching / resetting values */
                logger.warning("Wrong password, the entered password \"" + userEnteredPassword + "\" is wrong, retrying...");
                link.setDownloadPassword(null);
                throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
            }
            if (correctedBR.contains("Wrong captcha")) {
                logger.warning("Wrong captcha or wrong password!");
                throw new PluginException(LinkStatus.ERROR_CAPTCHA);
            }
            if (correctedBR.contains("\">Skipped countdown<")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Fatal countdown error (countdown skipped)");
            }
        }
        /** Wait time reconnect handling */
        final String limitBasedOnNumberofFilesAndTime = new Regex(correctedBR, ">(You have reached the maximum limit \\d+ files in \\d+ hours)").getMatch(0);
        if (new Regex(correctedBR, "(You have reached the download(\\-| )limit|You have to wait)").matches()) {
            /* adjust this regex to catch the wait time string for COOKIE_HOST */
            String wait = new Regex(correctedBR, "((You have reached the download(\\-| )limit|You have to wait)[^<>]+)").getMatch(0);
            String tmphrs = new Regex(wait, "\\s+(\\d+)\\s+hours?").getMatch(0);
            if (tmphrs == null) {
                tmphrs = new Regex(correctedBR, "You have to wait.*?\\s+(\\d+)\\s+hours?").getMatch(0);
            }
            String tmpmin = new Regex(wait, "\\s+(\\d+)\\s+minutes?").getMatch(0);
            if (tmpmin == null) {
                tmpmin = new Regex(correctedBR, "You have to wait.*?\\s+(\\d+)\\s+minutes?").getMatch(0);
            }
            String tmpsec = new Regex(wait, "\\s+(\\d+)\\s+seconds?").getMatch(0);
            String tmpdays = new Regex(wait, "\\s+(\\d+)\\s+days?").getMatch(0);
            if (tmphrs == null && tmpmin == null && tmpsec == null && tmpdays == null) {
                logger.info("Waittime regexes seem to be broken");
                if (account != null) {
                    throw new AccountUnavailableException("Download limit reached", 60 * 60 * 1000l);
                } else {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, 60 * 60 * 1000l);
                }
            } else {
                int minutes = 0, seconds = 0, hours = 0, days = 0;
                if (tmphrs != null) {
                    hours = Integer.parseInt(tmphrs);
                }
                if (tmpmin != null) {
                    minutes = Integer.parseInt(tmpmin);
                }
                if (tmpsec != null) {
                    seconds = Integer.parseInt(tmpsec);
                }
                if (tmpdays != null) {
                    days = Integer.parseInt(tmpdays);
                }
                int waittime = ((days * 24 * 3600) + (3600 * hours) + (60 * minutes) + seconds + 1) * 1000;
                logger.info("Detected waittime #2, waiting " + waittime + "milliseconds");
                /* Not enough wait time to reconnect -> Wait short and retry */
                if (waittime < 180000) {
                    throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Wait until new downloads can be started", waittime);
                }
                if (account != null) {
                    throw new AccountUnavailableException("Download limit reached", waittime);
                } else {
                    throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, null, waittime);
                }
            }
        } else if (limitBasedOnNumberofFilesAndTime != null) {
            /*
             * 2019-05-09: New: Seems like XFS owners can even limit by number of files inside specified timeframe. Example: hotlink.cc; 150
             * files per 24 hours
             */
            /* Typically '>You have reached the maximum limit 150 files in 24 hours' */
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, limitBasedOnNumberofFilesAndTime);
        } else if (correctedBR.contains("You're using all download slots for IP")) {
            throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Server error 'You're using all download slots for IP'", 10 * 60 * 1001l);
        } else if (correctedBR.contains("Error happened when generating Download Link")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Error happened when generating Download Link'", 10 * 60 * 1000l);
        }
        /** Error handling for premiumonly links */
        if (isPremiumOnlyHTML()) {
            String filesizelimit = new Regex(correctedBR, "You can download files up to(.*?)only").getMatch(0);
            if (filesizelimit != null) {
                filesizelimit = filesizelimit.trim();
                throw new AccountRequiredException("As free user you can download files up to " + filesizelimit + " only");
            } else {
                logger.info("Only downloadable via premium");
                throw new AccountRequiredException();
            }
        } else if (isPremiumOnlyURL()) {
            logger.info("Only downloadable via premium");
            throw new AccountRequiredException();
        } else if (correctedBR.contains(">Expired download session")) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 'Expired download session'", 10 * 60 * 1000l);
        }
        if (isWebsiteUnderMaintenance()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Host is under maintenance", 2 * 60 * 60 * 1000l);
        }
        checkResponseCodeErrors(br.getHttpConnection());
    }

    /** Handles all kinds of error-responsecodes! */
    public void checkResponseCodeErrors(final URLConnectionAdapter con) throws PluginException {
        if (con == null) {
            return;
        }
        final long responsecode = con.getResponseCode();
        if (responsecode == 403) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 403", 5 * 60 * 1000l);
        } else if (responsecode == 404) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404#1", 5 * 60 * 1000l);
        } else if (responsecode == 503) {
            throw new PluginException(LinkStatus.ERROR_HOSTER_TEMPORARILY_UNAVAILABLE, "Server error 503 connection limit reached, please contact our support!", 5 * 60 * 1000l);
        }
    }

    /**
     * Handles all kinds of errors which can happen if we get the final downloadlink but we get html code instead of the file we want to
     * download.
     */
    public void checkServerErrors() throws NumberFormatException, PluginException {
        if (new Regex(correctedBR.trim(), "^No file$").matches()) {
            /* Possibly dead file but it is supposed to be online so let's wait and retry! */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
        } else if (new Regex(correctedBR.trim(), "^Wrong IP$").matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: 'Wrong IP'", 2 * 60 * 60 * 1000l);
        } else if (new Regex(correctedBR.trim(), "^Expired$").matches()) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error: 'Expired'", 2 * 60 * 60 * 1000l);
        } else if (new Regex(correctedBR.trim(), "(^File Not Found$|<h1>404 Not Found</h1>)").matches()) {
            /* most likely result of generated link that has expired -raztoki */
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 404", 30 * 60 * 1000l);
        }
    }

    /**
     * Is intended to handle out of date errors which might occur seldom by re-tring a couple of times before throwing the out of date
     * error.
     *
     * @param dl
     *            : The DownloadLink
     * @param error
     *            : The name of the error
     * @param maxRetries
     *            : Max retries before out of date error is thrown
     */
    protected void handlePluginBroken(final DownloadLink dl, final String error, final int maxRetries) throws PluginException {
        final String host = this.getHost();
        int timesFailed = dl.getIntegerProperty(host + "failedtimes_" + error, 0);
        if (timesFailed <= maxRetries) {
            logger.info(error + " -> Retrying");
            timesFailed++;
            dl.setProperty(host + "failedtimes_" + error, timesFailed);
            throw new PluginException(LinkStatus.ERROR_RETRY, "Unknown error occured: " + error);
        } else {
            logger.info(error + " -> Plugin is broken");
            dl.setProperty(host + "failedtimes_" + error, Property.NULL);
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
    }

    protected boolean supports_lifetime_account() {
        return false;
    }

    protected boolean is_lifetime_account() {
        return new Regex(correctedBR, ">Premium account expire</TD><TD><b>Lifetime</b>").matches();
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, true);
        /* Only access URL if we haven't accessed it before already. */
        if (br.getURL() == null || !br.getURL().contains("/?op=my_account")) {
            getPage(this.getMainPage() + "/?op=my_account");
        }
        final String space[] = new Regex(correctedBR, ">Used space:</td>.*?<td.*?b>([0-9\\.]+) ?(KB|MB|GB|TB)?</b>").getRow(0);
        if ((space != null && space.length != 0) && (space[0] != null && space[1] != null)) {
            /* free users it's provided by default */
            ai.setUsedSpace(space[0] + " " + space[1]);
        } else if ((space != null && space.length != 0) && space[0] != null) {
            /* premium users the Mb value isn't provided for some reason... */
            ai.setUsedSpace(space[0] + "Mb");
        }
        String availabletraffic = regExTrafficLeft();
        /* Example non english: brupload.net */
        final boolean userHasUnlimitedTraffic = availabletraffic != null && availabletraffic.matches(".*?nlimited|Ilimitado.*?");
        if (availabletraffic != null && !userHasUnlimitedTraffic && !availabletraffic.equalsIgnoreCase(" Mb")) {
            availabletraffic.trim();
            /* need to set 0 traffic left, as getSize returns positive result, even when negative value supplied. */
            long trafficLeft = 0;
            if (!availabletraffic.startsWith("-")) {
                trafficLeft = (SizeFormatter.getSize(availabletraffic));
            } else {
                trafficLeft = 0;
            }
            /* 2019-02-19: Users can buy additional traffic packages: Example(s): subyshare.com */
            final String usableBandwidth = br.getRegex("Usable Bandwidth\\s*<span.*?>\\s*([0-9\\.]+\\s*[TGMKB]+)\\s*/\\s*[0-9\\.]+\\s*[TGMKB]+\\s*<").getMatch(0);
            if (usableBandwidth != null) {
                trafficLeft = Math.max(trafficLeft, SizeFormatter.getSize(usableBandwidth));
            }
            ai.setTrafficLeft(trafficLeft);
        } else {
            ai.setUnlimitedTraffic();
        }
        if (supports_lifetime_account() && is_lifetime_account()) {
            ai.setValidUntil(-1);
            account.setType(AccountType.LIFETIME);
            account.setMaxSimultanDownloads(getMaxSimultanPremiumDownloadNum());
            account.setConcurrentUsePossible(true);
        } else {
            /* If the premium account is expired or we cannot find an expire-date we'll simply accept it as a free account. */
            final String expire = new Regex(correctedBR, "(\\d{1,2} (January|February|March|April|May|June|July|August|September|October|November|December) \\d{4})").getMatch(0);
            long expire_milliseconds = 0;
            long expire_milliseconds_from_expiredate = 0;
            long expire_milliseconds_precise_to_the_second = 0;
            if (expire != null) {
                expire_milliseconds_from_expiredate = TimeFormatter.getMilliSeconds(expire, "dd MMMM yyyy", Locale.ENGLISH);
            }
            final boolean supports_precise_expire_date = this.supports_precise_expire_date();
            if (supports_precise_expire_date) {
                /*
                 * A more accurate expire time, down to the second. Usually shown on 'extend premium account' page. Case[0] e.g.
                 * 'flashbit.cc', Case [1] e.g. takefile.link, example website which has no precise expiredate at all: anzfile.net
                 */
                final String[] paymentURLs = new String[] { "/?op=payments", "/upgrade" };
                for (final String paymentURL : paymentURLs) {
                    try {
                        getPage(paymentURL);
                    } catch (final Throwable e) {
                        continue;
                    }
                    String expireSecond = new Regex(correctedBR, Pattern.compile("<div class=\"accexpire\">.*?</div>", Pattern.CASE_INSENSITIVE)).getMatch(-1);
                    if (StringUtils.isEmpty(expireSecond)) {
                        expireSecond = new Regex(correctedBR, Pattern.compile("Premium(-| )Account expires?\\s*:\\s*(?:</span>)?\\s*(?:<span>)?\\s*([a-zA-Z0-9, ]+)\\s*</", Pattern.CASE_INSENSITIVE)).getMatch(1);
                    }
                    if (StringUtils.isEmpty(expireSecond)) {
                        /*
                         * Last attempt - wider RegEx but we expect the 'second(s)' value to always be present!! Example: file-up.org:
                         * "<p style="direction: ltr; display: inline-block;">1 year, 352 days, 22 hours, 36 minutes, 45 seconds</p>"
                         */
                        /**
                         * TODO: 2019-02-21: This may lead to false-positives thus it may happen that free accounts get recognized as
                         * premium! Maybe change RegEx like this: 'blabla, minutes, seconds' (minutes AND seconds required) ...
                         */
                        expireSecond = new Regex(correctedBR, Pattern.compile(">\\s*(\\d+ years?, )?(\\d+ days?, )?(\\d+ hours?, )?(\\d+ minutes?, )?\\d+ seconds\\s*<", Pattern.CASE_INSENSITIVE)).getMatch(-1);
                    }
                    if (!StringUtils.isEmpty(expireSecond)) {
                        String tmpYears = new Regex(expireSecond, "(\\d+)\\s+years?").getMatch(0);
                        String tmpdays = new Regex(expireSecond, "(\\d+)\\s+days?").getMatch(0);
                        String tmphrs = new Regex(expireSecond, "(\\d+)\\s+hours?").getMatch(0);
                        String tmpmin = new Regex(expireSecond, "(\\d+)\\s+minutes?").getMatch(0);
                        String tmpsec = new Regex(expireSecond, "(\\d+)\\s+seconds?").getMatch(0);
                        long years = 0, days = 0, hours = 0, minutes = 0, seconds = 0;
                        if (!StringUtils.isEmpty(tmpYears)) {
                            years = Integer.parseInt(tmpYears);
                        }
                        if (!StringUtils.isEmpty(tmpdays)) {
                            days = Integer.parseInt(tmpdays);
                        }
                        if (!StringUtils.isEmpty(tmphrs)) {
                            hours = Integer.parseInt(tmphrs);
                        }
                        if (!StringUtils.isEmpty(tmpmin)) {
                            minutes = Integer.parseInt(tmpmin);
                        }
                        if (!StringUtils.isEmpty(tmpsec)) {
                            seconds = Integer.parseInt(tmpsec);
                        }
                        expire_milliseconds_precise_to_the_second = ((years * 86400000 * 365) + (days * 86400000) + (hours * 3600000) + (minutes * 60000) + (seconds * 1000)) + System.currentTimeMillis();
                    }
                    if (expire_milliseconds_precise_to_the_second > 0) {
                        logger.info("Successfully found precise expire-date via paymentURL: \"" + paymentURL + "\"");
                        break;
                    } else {
                        logger.info("Failed to find precise expire-date via paymentURL: \"" + paymentURL + "\"");
                    }
                }
            }
            // final boolean trust_expire_milliseconds_from_expiredate = expire_milliseconds_from_expiredate > 0;
            final boolean trust_expire_milliseconds_precise_to_the_second = expire_milliseconds_from_expiredate - expire_milliseconds_precise_to_the_second <= 24 * 60 * 60 * 1000;
            if (trust_expire_milliseconds_precise_to_the_second && expire_milliseconds_precise_to_the_second > 0) {
                /*
                 * Prefer more precise expire-date as long as it is max. 48 hours shorter than the other expire-date which is only exact up
                 * to 24 hours (up to the last day).
                 */
                logger.info("Using precise expire-date");
                expire_milliseconds = expire_milliseconds_precise_to_the_second;
            } else if (expire_milliseconds_from_expiredate > 0) {
                logger.info("Using expire-date which is up to 24 hours precise");
                expire_milliseconds = expire_milliseconds_from_expiredate;
            } else {
                logger.info("Failed to find any expire-date at all");
            }
            if ((expire_milliseconds - System.currentTimeMillis()) <= 0) {
                /* Expired premium or no expire date given --> It is usually a Free Account */
                account.setType(AccountType.FREE);
                account.setConcurrentUsePossible(false);
                account.setMaxSimultanDownloads(getMaxSimultaneousFreeAccountDownloads());
            } else {
                /* Expire date is in the future --> It is a premium account */
                ai.setValidUntil(expire_milliseconds);
                account.setType(AccountType.PREMIUM);
                account.setConcurrentUsePossible(true);
                account.setMaxSimultanDownloads(this.getMaxSimultanPremiumDownloadNum());
            }
        }
        return ai;
    }

    public Form findLoginform(final Browser br) {
        Form loginform = br.getFormbyProperty("name", "FL");
        if (loginform == null) {
            /* More complicated way to find loginform ... */
            final Form[] allForms = this.br.getForms();
            for (final Form aForm : allForms) {
                final InputField inputFieldOP = aForm.getInputFieldByName("op");
                if (inputFieldOP != null && "login".equalsIgnoreCase(inputFieldOP.getValue())) {
                    loginform = aForm;
                    break;
                }
            }
        }
        return loginform;
    }

    /** Returns Form required to click on 'continue to image' for image-hosts. */
    public Form findImageForm(final Browser br) {
        return br.getFormbyKey("next");
    }

    protected String regExTrafficLeft() {
        return regExTrafficLeft(this.correctedBR);
    }

    /** Tries to find available traffic inside html code. */
    public String regExTrafficLeft(final String source) {
        /* Traffic can also be negative! */
        String availabletraffic = new Regex(source, "Traffic available[^<>]*?:?</TD><TD><b>([^<>\"']+)</b>").getMatch(0);
        if (availabletraffic == null) {
            /* 2019-02-11: For newer XFS versions */
            availabletraffic = new Regex(source, ">Traffic available(?: today)?</div>\\s*?<div class=\"txt\\d+\">([^<>\"]+)<").getMatch(0);
        }
        return availabletraffic;
    }

    /**
     * Checks logged-in state via given HTML code.
     *
     * @return true: Implies that user is logged-in. <br />
     *         false: Implies that user is not logged-in. <br />
     *         default: true
     */
    public boolean isLoggedinHTML() {
        return br.containsHTML("op=logout");
    }

    public String getLoginURL() {
        return getMainPage() + "/login.html";
    }

    public void login(final Account account, final boolean force) throws Exception {
        synchronized (account) {
            try {
                /* Load cookies */
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                boolean loggedInViaCookies = false;
                if (cookies != null) {
                    br.setCookies(getMainPage(), cookies);
                    if (System.currentTimeMillis() - account.getCookiesTimeStamp("") <= 300000l && !force) {
                        /* We trust these cookies as they're not that old --> Do not check them */
                        logger.info("Trust cookies without checking as they're still fresh");
                        return;
                    }
                    logger.info("Verifying login-cookies");
                    getPage(getMainPage() + "/");
                    loggedInViaCookies = isLoggedinHTML();
                }
                if (loggedInViaCookies) {
                    /* No additional check required --> We know cookies are valid and we're logged in --> Done! */
                    logger.info("Successfully logged in via cookies");
                } else {
                    logger.info("Performing full login");
                    br.clearCookies(getMainPage());
                    getPage(getLoginURL());
                    if (br.getHttpConnection().getResponseCode() == 404) {
                        /* Required for some XFS setups. */
                        getPage(getMainPage() + "/login");
                    }
                    Form loginform = findLoginform(this.br);
                    if (loginform == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    loginform.put("login", Encoding.urlEncode(account.getUser()));
                    loginform.put("password", Encoding.urlEncode(account.getPass()));
                    if (br.containsHTML("class=\"g\\-recaptcha\"")) {
                        final DownloadLink dlinkbefore = this.getDownloadLink();
                        final DownloadLink dl_dummy;
                        if (dlinkbefore != null) {
                            dl_dummy = dlinkbefore;
                        } else {
                            dl_dummy = new DownloadLink(this, "Account", this.getHost(), "https://" + account.getHoster(), true);
                            this.setDownloadLink(dl_dummy);
                        }
                        handleCaptcha(dl_dummy, loginform);
                        if (dlinkbefore != null) {
                            this.setDownloadLink(dlinkbefore);
                        }
                    }
                    submitForm(loginform);
                    /* Missing login cookies or we still have the loginform --> Login failed */
                    final boolean loginCookieOkay = br.getCookie(getMainPage(), "login") != null || br.getCookie(getMainPage(), "xfss") != null;
                    final boolean loginFormOkay = findLoginform(this.br) == null;
                    final boolean loginURLOkay = br.getURL().contains("op=") && !br.getURL().contains("op=login");
                    if (!loginCookieOkay && !loginFormOkay && !loginURLOkay) {
                        if (correctedBR.contains("op=resend_activation")) {
                            /* User entered correct logindata but has not activated his account ... */
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nYour account has not yet been activated!\r\nActivate it via the URL you should have received via E-Mail and try again!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger Benutzername, Passwort oder login Captcha!\r\nDu bist dir sicher, dass dein eingegebener Benutzername und Passwort stimmen? Versuche folgendes:\r\n1. Falls dein Passwort Sonderzeichen enthält, ändere es (entferne diese) und versuche es erneut!\r\n2. Gib deine Zugangsdaten per Hand (ohne kopieren/einfügen) ein.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłędny użytkownik/hasło lub kod Captcha wymagany do zalogowania!\r\nUpewnij się, że prawidłowo wprowadziłes hasło i nazwę użytkownika. Dodatkowo:\r\n1. Jeśli twoje hasło zawiera znaki specjalne, zmień je (usuń) i spróbuj ponownie!\r\n2. Wprowadź hasło i nazwę użytkownika ręcznie bez użycia opcji Kopiuj i Wklej.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password or login captcha!\r\nYou're sure that the username and password you entered are correct? Some hints:\r\n1. If your password contains special characters, change it (remove them) and try again!\r\n2. Type in your username/password by hand without copy & paste.", PluginException.VALUE_ID_PREMIUM_DISABLE);
                        }
                    }
                }
                account.saveCookies(br.getCookies(getMainPage()), "");
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            }
        }
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        /* Perform linkcheck without logging in */
        requestFileInformation(link);
        login(account, false);
        final String directlinkproperty = getDownloadModeDirectlinkProperty(account);
        if (AccountType.FREE.equals(account.getType())) {
            /* Perform linkcheck after logging in */
            requestFileInformation(link);
            doFree(link, account);
        } else {
            String dllink = checkDirectLink(link, directlinkproperty);
            if (dllink == null) {
                getPage(link.getPluginPatternMatcher());
                dllink = getDllink(link, account);
                if (dllink == null) {
                    final Form dlform = findFormF1Premium();
                    if (dlform != null && isPasswordProtected()) {
                        handlePassword(dlform, link);
                    }
                    checkErrors(link, account, true);
                    if (dlform == null) {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    submitForm(dlform);
                    checkErrors(link, account, true);
                    dllink = getDllink(link, account);
                }
            }
            handleDownload(link, account, dllink);
        }
    }

    protected void handleDownload(final DownloadLink link, final Account account, final String dllink) throws Exception {
        if (dllink == null) {
            logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        final boolean resume = this.isResumeable(link, account);
        final int maxChunks = getMaxChunks(account);
        final String directlinkproperty = getDownloadModeDirectlinkProperty(account);
        logger.info("Final downloadlink = " + dllink + " starting the download...");
        if (dllink.startsWith("rtmp")) {
            try {
                dl = new RTMPDownload(this, link, dllink);
            } catch (final NoClassDefFoundError e) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "RTMPDownload class missing");
            }
            final String playpath = new Regex(dllink, "(mp4:.+)").getMatch(0);
            /* Setup rtmp connection */
            jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();
            rtmp.setPageUrl(link.getPluginPatternMatcher());
            rtmp.setUrl(dllink);
            if (playpath != null) {
                rtmp.setPlayPath(playpath);
            }
            rtmp.setFlashVer("WIN 25,0,0,148");
            rtmp.setSwfVfy("CHECK_ME");
            rtmp.setApp("vod/");
            rtmp.setResume(false);
            fixFilename(link);
            try {
                /* add a download slot */
                if (account == null) {
                    controlFree(+1);
                }
                /* start the dl */
                ((RTMPDownload) dl).startDownload();
            } finally {
                /* remove download slot */
                if (account == null) {
                    controlFree(-1);
                }
            }
        } else {
            /*
             * Save directurl before download-attempt as it should be valid even if it e.g. fails because of server issue 503 (= too many
             * connections) --> Should work fine after the next try.
             */
            link.setProperty(directlinkproperty, dllink);
            dl = new jd.plugins.BrowserAdapter().openDownload(br, link, dllink, resume, maxChunks);
            if (dl.getConnection().getContentType().contains("html")) {
                checkResponseCodeErrors(dl.getConnection());
                logger.warning("The final dllink seems not to be a file!");
                br.followConnection();
                correctBR();
                checkServerErrors();
                handlePluginBroken(link, "dllinknofile", 3);
            }
            fixFilename(link);
            try {
                /* add a download slot */
                if (account == null) {
                    controlFree(+1);
                }
                /* start the dl */
                dl.startDownload();
            } finally {
                /* remove download slot */
                if (account == null) {
                    controlFree(-1);
                }
            }
        }
    }

    /**
     * pseudo redirect control!
     */
    @Override
    protected void runPostRequestTask(Browser ibr) throws Exception {
        final String redirect;
        if (!ibr.isFollowingRedirects() && (redirect = ibr.getRedirectLocation()) != null) {
            if (!this.isImagehoster()) {
                if (!isDllinkFile(redirect)) {
                    super.getPage(ibr, redirect);
                    return;
                }
            } else {
                if (!isDllinkImage(redirect)) {
                    super.getPage(ibr, redirect);
                    return;
                }
            }
        }
    }

    /**
     * Use this to set filename based on filename inside URL or fuid as filename either before a linkcheck happens so that there is a
     * readable filename displayed in the linkgrabber or also for mass-linkchecking as in this case these is no filename given inside HTML.
     */
    protected void setWeakFilename(final DownloadLink link) {
        final String weak_fallback_filename = this.getFallbackFilename(link);
        /* Set fallback_filename if no better filename has ever been set before. */
        final boolean setWeakFilename = link.getName() == null || (weak_fallback_filename != null && weak_fallback_filename.length() > link.getName().length());
        if (setWeakFilename) {
            link.setName(weak_fallback_filename);
            /* TODO: Find better way to determine whether a String contains a file-extension or not. */
            final boolean fallback_filename_contains_file_extension = weak_fallback_filename != null && weak_fallback_filename.contains(".");
            if (!fallback_filename_contains_file_extension) {
                /* Only setMimeHint if weak filename does not contain filetype. */
                if (this.isAudiohoster()) {
                    link.setMimeHint(CompiledFiletypeFilter.AudioExtensions.MP3);
                } else if (this.isImagehoster()) {
                    link.setMimeHint(CompiledFiletypeFilter.ImageExtensions.JPG);
                } else if (this.isVideohoster_enforce_video_filename()) {
                    link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
                }
            }
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
        if (!DebugMode.TRUE_IN_IDE_ELSE_FALSE && link != null) {
            /* Reset directurl-properties in stable, NOT in dev mode */
            /*
             * TODO 2019-04-05: Either just don't do this or find a better solution for this. This will cause unnecessary captchas and will
             * just waste the users' hard work of generating direct-URLs (e.g. entering captchas). I have never seen a situation in which
             * one of our plugins e.g. looped forever because of bad directlink handling. This plugin is designed to verify directlinks and
             * automatically delete that property once a directlink is not valid anymore!
             */
            link.removeProperty("freelink2");
            link.removeProperty("premlink");
            link.removeProperty("freelink");
        }
    }

    @Override
    public SiteTemplate siteTemplateType() {
        return SiteTemplate.SibSoft_XFileShare;
    }
}