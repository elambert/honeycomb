/*
 * Copyright © 2008, Sun Microsystems, Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 *    * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 *    * Neither the name of Sun Microsystems, Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */



#include <sys/devops.h>  /* used by dev_ops */
#include <sys/conf.h>    /* used by dev_ops and cb_ops */
#include <sys/modctl.h>  /* used by modlinkage, modldrv, _init, _info, */
                         /* and _fini */
#include <sys/types.h>   /* used by open, close, read, write, prop_op, */
                         /* and ddi_prop_op */
#include <sys/file.h>    /* used by open, close */
#include <sys/errno.h>   /* used by open, close, read, write */
#include <sys/open.h>    /* used by open, close, read, write */
#include <sys/cred.h>    /* used by open, close, read */
#include <sys/uio.h>     /* used by read */
#include <sys/stat.h>    /* defines S_IFCHR used by ddi_create_minor_node */
#include <sys/cmn_err.h> /* used by all entry points for this driver */
#include <sys/ddi.h>     /* used by all entry points for this driver */
                         /* also used by cb_ops, ddi_get_instance, and */
                         /* ddi_prop_op */
#include <sys/sunddi.h>  /* used by all entry points for this driver */
                         /* also used by cb_ops, ddi_create_minor_node, */
                         /* ddi_get_instance, and ddi_prop_op */

#define BUFSIZE 300
#define PANIC_STR "panic"

static int hctestmod_attach(dev_info_t *dip, ddi_attach_cmd_t cmd);
static int hctestmod_detach(dev_info_t *dip, ddi_detach_cmd_t cmd);
static int hctestmod_getinfo(dev_info_t *dip, ddi_info_cmd_t cmd, void *arg,
    void **resultp);
static int hctestmod_prop_op(dev_t dev, dev_info_t *dip, ddi_prop_op_t prop_op,
    int flags, char *name, caddr_t valuep, int *lengthp);
static int hctestmod_open(dev_t *devp, int flag, int otyp, cred_t *cred);
static int hctestmod_close(dev_t dev, int flag, int otyp, cred_t *cred);
static int hctestmod_read(dev_t dev, struct uio *uiop, cred_t *credp);
static int hctestmod_write(dev_t dev, struct uio *uiop, cred_t *credp);

/* cb_ops structure */
static struct cb_ops hctestmod_cb_ops = {
    hctestmod_open,
    hctestmod_close,
    nodev,              /* no strategy - nodev returns ENXIO */
    nodev,              /* no print */
    nodev,              /* no dump */
    hctestmod_read,
    hctestmod_write,
    nodev,              /* no ioctl */
    nodev,              /* no devmap */
    nodev,              /* no mmap */
    nodev,              /* no segmap */
    nochpoll,           /* returns ENXIO for non-pollable devices */
    hctestmod_prop_op,
    NULL,               /* streamtab struct; if not NULL, all above */
                        /* fields are ignored */
    D_NEW | D_MP,       /* compatibility flags: see conf.h */
    CB_REV,             /* cb_ops revision number */
    nodev,              /* no aread */
    nodev               /* no awrite */
};

/* dev_ops structure */
static struct dev_ops hctestmod_dev_ops = {
    DEVO_REV,
    0,                  /* reference count */
    hctestmod_getinfo,
    nulldev,            /* no identify - nulldev returns 0 */
    nulldev,            /* no probe */
    hctestmod_attach,
    hctestmod_detach,
    nodev,              /* no reset - nodev returns ENXIO */
    &hctestmod_cb_ops,
    (struct bus_ops *)NULL,
    nodev               /* no power */
};

/* modldrv structure */
static struct modldrv md = {
    &mod_driverops,     /* Type of module. This is a driver. */
    "hctestmod driver",     /* Name of the module. */
    &hctestmod_dev_ops
};

/* modlinkage structure */
static struct modlinkage ml = {
    MODREV_1,
    &md,
    NULL
};

/* dev_info structure */
dev_info_t *hctestmod_dip;  /* keep track of one instance */


/* Loadable module configuration entry points */
int
_init(void)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside _init");
#endif
    return(mod_install(&ml));
}

int
_info(struct modinfo *modinfop)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside _info");
#endif
    return(mod_info(&ml, modinfop));
}

int
_fini(void)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside _fini");
#endif
    return(mod_remove(&ml));
}

/* Device configuration entry points */
static int
hctestmod_attach(dev_info_t *dip, ddi_attach_cmd_t cmd)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside hctestmod_attach");
#endif
    switch(cmd) {
    case DDI_ATTACH:
        hctestmod_dip = dip;
        if (ddi_create_minor_node(dip, "0", S_IFCHR,
            ddi_get_instance(dip), DDI_PSEUDO,0)
            != DDI_SUCCESS) {
            cmn_err(CE_NOTE,
                "%s%d: attach: could not add character node.",
                "hctestmod", 0);
            return(DDI_FAILURE);
        } else
            return DDI_SUCCESS;
    default:
        return DDI_FAILURE;
    }
}

static int
hctestmod_detach(dev_info_t *dip, ddi_detach_cmd_t cmd)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside hctestmod_detach");
#endif
    switch(cmd) {
    case DDI_DETACH:
        hctestmod_dip = 0;
        ddi_remove_minor_node(dip, NULL);
        return DDI_SUCCESS;
    default:
        return DDI_FAILURE;
    }
}

static int
hctestmod_getinfo(dev_info_t *dip, ddi_info_cmd_t cmd, void *arg, 
    void **resultp)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside hctestmod_getinfo");
#endif
    switch(cmd) {
    case DDI_INFO_DEVT2DEVINFO:
        *resultp = hctestmod_dip;
        return DDI_SUCCESS;
    case DDI_INFO_DEVT2INSTANCE:
        *resultp = 0;
        return DDI_SUCCESS;
    default:
        return DDI_FAILURE;
    }
}

/* Main entry points */
static int
hctestmod_prop_op(dev_t dev, dev_info_t *dip, ddi_prop_op_t prop_op,
    int flags, char *name, caddr_t valuep, int *lengthp)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside hctestmod_prop_op");
#endif
    return(ddi_prop_op(dev,dip,prop_op,flags,name,valuep,lengthp));
}

static int
hctestmod_open(dev_t *devp, int flag, int otyp, cred_t *cred)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside hctestmod_open");
#endif
    return DDI_SUCCESS;
}

static int
hctestmod_close(dev_t dev, int flag, int otyp, cred_t *cred)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside hctestmod_close");
#endif
    return DDI_SUCCESS;
}

static int
hctestmod_read(dev_t dev, struct uio *uiop, cred_t *credp)
{
#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside hctestmod_read");
#endif
    return DDI_SUCCESS;
}

/*
 * This is the routine that does the processing of user input to the
 * device.
 */
static int
hctestmod_write(dev_t dev, struct uio *uiop, cred_t *credp)
{
    size_t len;
    char msg[BUFSIZE];
    int retval;

#ifdef DEBUG
    cmn_err(CE_NOTE, "Inside hctestmod_write");
#endif
    if (uiop == NULL) {
        cmn_err(CE_NOTE, "null in write");
        return (EINVAL);
    }

    len = uiop->uio_resid;
    if (len > BUFSIZE) {
        cmn_err(CE_NOTE, "truncating output from %lu to %d", len, BUFSIZE);
        len = BUFSIZE;
    }

#ifdef DEBUG
    cmn_err(CE_NOTE, "what follows is from hctestmod, msg len %lu", len);
#endif

    retval = uiomove((void*)msg, len, UIO_WRITE, uiop);
    if (retval != 0) {
        return (retval);
    }

    /* replace newline char to null terminate the string (assumes echo used) */
    if (len > 0) {
        msg[len - 1] = '\0';
    } else {
        cmn_err(CE_NOTE, "msg length was not greater than 0 but was %lu", len);
        return (DDI_SUCCESS);
    }

    /* panic the system using this tool if the right string is passed */
    if (strcmp(PANIC_STR, msg) == 0) {
        panic("hctestmod received user request for node panic");
    }

    /* for any other string, we just echo what we are passed */
    cmn_err(CE_WARN, "%s", msg);
    return retval;
}
